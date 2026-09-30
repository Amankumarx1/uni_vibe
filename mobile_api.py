"""
Mobile (Android) API layer for UniVibe.

Design:
  * Token auth: the app logs in at POST /api/v1/auth/login and receives a signed bearer token.
  * `init_mobile_auth(app)` registers a before_request hook that turns a valid
    `Authorization: Bearer <token>` header into the same `session` values the website uses.
    Result: every existing /api/* JSON endpoint (discover, action, messages, wall, safety...)
    works for the app unchanged.
  * This blueprint adds the endpoints the website only offered as HTML pages
    (me, matches, network, conversations, notifications, profile view, wall feed).
"""
import json
from flask import Blueprint, jsonify, request, session
from itsdangerous import URLSafeTimedSerializer, BadSignature, SignatureExpired
from werkzeug.security import check_password_hash

from config import Config
from database.db import query_db, execute_db, get_current_time_str
from services.rate_limiter import is_rate_limited, reset_rate_limit, get_client_ip
from services.date_utils import calculate_age
from services.moderation import is_blocked

mobile_bp = Blueprint('mobile_api', __name__, url_prefix='/api/v1')

TOKEN_MAX_AGE = 60 * 60 * 24 * 30  # 30 days
DEFAULT_AVATAR = '/static/images/avatars/user_aman.webp'


# --------------------------------------------------------------------------- #
# Token helpers
# --------------------------------------------------------------------------- #
def _serializer():
    return URLSafeTimedSerializer(Config.SECRET_KEY, salt='univibe-mobile-v1')


def issue_token(user_id):
    return _serializer().dumps({'uid': int(user_id)})


def _read_token(token):
    try:
        data = _serializer().loads(token, max_age=TOKEN_MAX_AGE)
        return int(data['uid'])
    except (BadSignature, SignatureExpired, KeyError, ValueError, TypeError):
        return None


def init_mobile_auth(app):
    """Bridge Bearer tokens to the session-based auth used by existing routes."""

    @app.before_request
    def _bearer_to_session():
        if not request.path.startswith('/api/'):
            return None
        header = request.headers.get('Authorization', '')
        if not header.lower().startswith('bearer '):
            return None  # fall back to normal cookie session (website)

        uid = _read_token(header[7:].strip())
        if uid is None:
            return jsonify({'error': 'Unauthorized', 'code': 'invalid_token'}), 401

        user = query_db("SELECT id, is_admin, account_status, deleted_at FROM users WHERE id = ?",
                        (uid,), one=True)
        if not user or user.get('deleted_at') or user['account_status'] in (3, 4, 5):
            return jsonify({'error': 'Unauthorized', 'code': 'account_unavailable'}), 401

        # Pending accounts may only reach auth/me endpoints
        if (user['account_status'] == 1 and not user['is_admin']
                and not request.path.startswith(('/api/v1/auth', '/api/v1/me'))):
            return jsonify({'error': 'Account pending verification', 'code': 'pending'}), 403

        session.clear()
        session['user_id'] = user['id']
        session['is_admin'] = bool(user['is_admin'])
        profile = query_db("SELECT display_name, university_id, primary_photo_path "
                           "FROM user_profiles WHERE user_id = ?", (uid,), one=True)
        if profile:
            session['display_name'] = profile['display_name']
            session['university_id'] = profile['university_id']
            session['photo'] = profile['primary_photo_path'] or DEFAULT_AVATAR
        return None


def _uid():
    return session.get('user_id')


def _unauth():
    return jsonify({'error': 'Unauthorized'}), 401


def _photo(path):
    return path or DEFAULT_AVATAR


# --------------------------------------------------------------------------- #
# Auth
# --------------------------------------------------------------------------- #
@mobile_bp.route('/auth/login', methods=['POST'])
def login():
    data = request.get_json(silent=True) or {}
    email = str(data.get('email', '')).strip().lower()
    password = str(data.get('password', ''))
    if not email or not password:
        return jsonify({'error': 'Email and password are required.'}), 400

    ip = get_client_ip()
    limited_ip, retry_ip = is_rate_limited(f"login_ip_{ip}", max_attempts=12, window_seconds=300)
    limited_email, retry_email = is_rate_limited(f"login_email_{email}", max_attempts=6, window_seconds=300)
    if limited_ip or limited_email:
        wait = max(retry_ip, retry_email)
        return jsonify({'error': f'Too many attempts. Try again in {wait} seconds.'}), 429

    user = query_db("SELECT * FROM users WHERE email = ? AND deleted_at IS NULL", (email,), one=True)
    if not user or not check_password_hash(user['password_hash'], password):
        return jsonify({'error': 'Invalid email address or password.'}), 401

    status = user['account_status']
    if status == 3:
        from services.admin_service import check_and_expire_suspensions
        if check_and_expire_suspensions(user['id']):
            status = 2
        else:
            return jsonify({'error': 'This account is temporarily suspended.'}), 403
    elif status == 4:
        return jsonify({'error': 'This account has been permanently banned.'}), 403
    elif status == 5:
        return jsonify({'error': 'This account has been deactivated or deleted.'}), 403

    reset_rate_limit(f"login_ip_{ip}")
    reset_rate_limit(f"login_email_{email}")
    execute_db("UPDATE users SET last_login_at = ? WHERE id = ?", (get_current_time_str(), user['id']))

    profile = query_db("SELECT display_name FROM user_profiles WHERE user_id = ?", (user['id'],), one=True)
    if status == 1 and not user['is_admin']:
        next_step = 'pending_verification'
    elif not profile:
        next_step = 'complete_on_web'   # signup / onboarding not yet native
    else:
        next_step = 'app'

    return jsonify({
        'token': issue_token(user['id']),
        'user_id': user['id'],
        'next_step': next_step,
    })


@mobile_bp.route('/auth/logout', methods=['POST'])
def logout():
    # Tokens are stateless; the app just discards it.
    return jsonify({'success': True})


# --------------------------------------------------------------------------- #
# Me
# --------------------------------------------------------------------------- #
@mobile_bp.route('/me')
def me():
    uid = _uid()
    if not uid:
        return _unauth()

    user = query_db("SELECT id, email, account_status, is_admin FROM users WHERE id = ?", (uid,), one=True)
    profile = query_db("""
        SELECT p.*, u.name AS university_name, u.city AS university_city
        FROM user_profiles p JOIN universities u ON u.id = p.university_id
        WHERE p.user_id = ?""", (uid,), one=True)

    unread = query_db("SELECT COUNT(*) AS c FROM notifications WHERE user_id = ? AND is_read = 0",
                      (uid,), one=True)
    base = {
        'user_id': uid,
        'email': user['email'],
        'account_status': user['account_status'],
        'has_profile': bool(profile),
        'unread_notifications': unread['c'] if unread else 0,
    }
    if not profile:
        return jsonify(base)

    interests = query_db("""SELECT i.id, i.name, i.category FROM user_interests ui
                            JOIN interests i ON i.id = ui.interest_id WHERE ui.user_id = ?""", (uid,))
    modes = query_db("SELECT mode_id, enabled FROM user_modes WHERE user_id = ?", (uid,))
    verif = query_db("SELECT 1 AS ok FROM student_verifications WHERE user_id = ? AND status = 2",
                     (uid,), one=True)

    base.update({
        'display_name': profile['display_name'],
        'age': calculate_age(profile.get('birth_date')),
        'birth_date': profile.get('birth_date'),
        'course': profile.get('course'),
        'study_year': profile.get('study_year'),
        'bio': profile.get('bio') or '',
        'photo': _photo(profile.get('primary_photo_path')),
        'university_name': profile['university_name'],
        'university_city': profile['university_city'],
        'student_verified': bool(verif),
        'interests': interests,
        'modes': {str(m['mode_id']): bool(m['enabled']) for m in modes},
    })
    return jsonify(base)


# --------------------------------------------------------------------------- #
# Matches / connections / network
# --------------------------------------------------------------------------- #
@mobile_bp.route('/matches')
def matches():
    uid = _uid()
    if not uid:
        return _unauth()

    def rows(sql, params):
        out = []
        for r in query_db(sql, params):
            out.append({
                'user_id': r['user_id'],
                'display_name': r['display_name'],
                'course': r['course'],
                'photo': _photo(r['primary_photo_path']),
                'university_name': r['university_name'],
                'conversation_id': r['conversation_id'],
                'since': r['since'],
            })
        return out

    dating = rows("""
        SELECT m.conversation_id, m.matched_at AS since, p.user_id, p.display_name, p.course,
               p.primary_photo_path, u.name AS university_name
        FROM matches m
        JOIN user_profiles p ON p.user_id = (CASE WHEN m.user_a_id = ? THEN m.user_b_id ELSE m.user_a_id END)
        JOIN universities u ON u.id = p.university_id
        WHERE (m.user_a_id = ? OR m.user_b_id = ?) AND m.status = 1
        ORDER BY m.matched_at DESC""", (uid, uid, uid))

    def conn(mode):
        return rows("""
            SELECT c.conversation_id, c.created_at AS since, p.user_id, p.display_name, p.course,
                   p.primary_photo_path, u.name AS university_name
            FROM connections c
            JOIN user_profiles p ON p.user_id = (CASE WHEN c.user_a_id = ? THEN c.user_b_id ELSE c.user_a_id END)
            JOIN universities u ON u.id = p.university_id
            WHERE (c.user_a_id = ? OR c.user_b_id = ?) AND c.mode_id = ? AND c.status = 2
            ORDER BY c.created_at DESC""", (uid, uid, uid, mode))

    return jsonify({'dating': dating, 'friends': conn(2), 'networking': conn(3)})


@mobile_bp.route('/network')
def network():
    uid = _uid()
    if not uid:
        return _unauth()
    q = request.args.get('q', '').strip().lower()
    sql = """
        SELECT ud.user_id, ud.display_name, ud.course, ud.study_year, ud.bio,
               ud.primary_photo_path, ud.student_verified,
               u.name AS university_name, u.city AS university_city
        FROM user_discovery ud JOIN universities u ON u.id = ud.university_id
        WHERE ud.networking_enabled = 1 AND ud.is_discoverable = 1 AND ud.user_id != ?
          AND ud.user_id NOT IN (SELECT blocked_user_id FROM blocks WHERE blocker_user_id = ?
                                 UNION SELECT blocker_user_id FROM blocks WHERE blocked_user_id = ?)"""
    params = [uid, uid, uid]
    if q:
        sql += " AND (LOWER(ud.display_name) LIKE ? OR LOWER(ud.course) LIKE ? OR LOWER(u.name) LIKE ?)"
        params += [f"%{q}%"] * 3
    sql += " ORDER BY ud.last_active_at DESC LIMIT 30"
    members = [{
        'user_id': m['user_id'], 'display_name': m['display_name'], 'course': m['course'],
        'study_year': m['study_year'], 'bio': m['bio'] or '',
        'photo': _photo(m['primary_photo_path']),
        'student_verified': bool(m['student_verified']),
        'university_name': m['university_name'], 'university_city': m['university_city'],
    } for m in query_db(sql, tuple(params))]
    return jsonify({'members': members})


# --------------------------------------------------------------------------- #
# Public profile
# --------------------------------------------------------------------------- #
@mobile_bp.route('/profile/<int:target_id>')
def public_profile(target_id):
    uid = _uid()
    if not uid:
        return _unauth()
    if is_blocked(uid, target_id):
        return jsonify({'error': 'This profile is unavailable.'}), 403

    p = query_db("""
        SELECT p.user_id, p.university_id, p.display_name, p.birth_date, p.course, p.study_year,
               p.bio, p.primary_photo_path, u.name AS university_name, u.city AS university_city
        FROM user_profiles p JOIN universities u ON u.id = p.university_id
        WHERE p.user_id = ?""", (target_id,), one=True)
    if not p:
        return jsonify({'error': 'Profile not found.'}), 404

    privacy = query_db("SELECT * FROM user_privacy_settings WHERE user_id = ?", (target_id,), one=True)
    show_univ = bool(privacy['show_university']) if privacy else True
    show_course = bool(privacy['show_course']) if privacy else True
    show_year = bool(privacy['show_study_year']) if privacy else True

    interests = query_db("""SELECT i.id, i.name, i.category FROM user_interests ui
                            JOIN interests i ON i.id = ui.interest_id WHERE ui.user_id = ?""", (target_id,))
    mine = {r['interest_id'] for r in query_db("SELECT interest_id FROM user_interests WHERE user_id = ?", (uid,))}
    photos = [r['photo_path'] for r in query_db(
        "SELECT photo_path FROM user_photos WHERE user_id = ? ORDER BY display_order ASC", (target_id,))]
    prompts = [{'question': r['prompt_question'], 'answer': r['prompt_answer']} for r in query_db(
        "SELECT prompt_question, prompt_answer FROM profile_prompts WHERE user_id = ? ORDER BY display_order ASC",
        (target_id,))]
    verif = query_db("SELECT 1 AS ok FROM student_verifications WHERE user_id = ? AND status = 2",
                     (target_id,), one=True)

    primary = _photo(p['primary_photo_path'])
    all_photos = [primary] + [x for x in photos if x != primary]
    return jsonify({
        'user_id': p['user_id'],
        'display_name': p['display_name'],
        'age': calculate_age(p.get('birth_date')),
        'university_name': p['university_name'] if show_univ else None,
        'university_city': p['university_city'] if show_univ else None,
        'course': p['course'] if show_course else None,
        'study_year': p['study_year'] if show_year else None,
        'bio': p['bio'] or '',
        'photos': all_photos,
        'prompts': prompts,
        'interests': interests,
        'shared_tags': [i['name'] for i in interests if i['id'] in mine],
        'student_verified': bool(verif),
    })


# --------------------------------------------------------------------------- #
# Conversations & messages (polling-friendly; Socket.IO is not required)
# --------------------------------------------------------------------------- #
@mobile_bp.route('/conversations')
def conversations():
    uid = _uid()
    if not uid:
        return _unauth()
    rows = query_db("""
        SELECT c.id, c.last_message_text, c.last_message_at, cm.last_read_at,
               op.user_id AS partner_id, op.display_name AS partner_name,
               op.primary_photo_path AS partner_photo, un.name AS partner_university,
               (SELECT COUNT(*) FROM messages m
                 WHERE m.conversation_id = c.id AND m.sender_user_id != ?
                   AND m.created_at > COALESCE(cm.last_read_at, '1970-01-01 00:00:00')) AS unread
        FROM conversations c
        JOIN conversation_members cm ON cm.conversation_id = c.id AND cm.user_id = ?
        JOIN conversation_members ocm ON ocm.conversation_id = c.id AND ocm.user_id != ?
        JOIN user_profiles op ON op.user_id = ocm.user_id
        JOIN universities un ON un.id = op.university_id
        WHERE ocm.user_id NOT IN (SELECT blocked_user_id FROM blocks WHERE blocker_user_id = ?
                                  UNION SELECT blocker_user_id FROM blocks WHERE blocked_user_id = ?)
        ORDER BY c.last_message_at DESC""", (uid, uid, uid, uid, uid))
    return jsonify({'conversations': [{
        'id': r['id'],
        'partner_id': r['partner_id'],
        'partner_name': r['partner_name'],
        'partner_photo': _photo(r['partner_photo']),
        'partner_university': r['partner_university'],
        'last_message_text': r['last_message_text'] or '',
        'last_message_at': r['last_message_at'],
        'unread': r['unread'] or 0,
    } for r in rows]})


def _is_member(conv_id, uid):
    return bool(query_db("SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ?",
                         (conv_id, uid), one=True))


@mobile_bp.route('/conversations/<int:conv_id>/messages')
def conversation_messages(conv_id):
    """Latest messages, or only newer ones when ?after=<message_id> (used for polling)."""
    uid = _uid()
    if not uid:
        return _unauth()
    if not _is_member(conv_id, uid):
        return jsonify({'error': 'Forbidden'}), 403

    after = request.args.get('after', '')
    cols = "id, sender_user_id, body, media_type, media_url, created_at"
    if after.isdigit():
        msgs = query_db(f"SELECT {cols} FROM messages WHERE conversation_id = ? AND id > ? ORDER BY id ASC LIMIT 100",
                        (conv_id, int(after)))
    else:
        msgs = list(reversed(query_db(
            f"SELECT {cols} FROM messages WHERE conversation_id = ? ORDER BY id DESC LIMIT 60", (conv_id,))))
    return jsonify({'messages': msgs})


@mobile_bp.route('/conversations/<int:conv_id>/read', methods=['POST'])
def conversation_read(conv_id):
    uid = _uid()
    if not uid:
        return _unauth()
    if not _is_member(conv_id, uid):
        return jsonify({'error': 'Forbidden'}), 403
    execute_db("UPDATE conversation_members SET last_read_at = ? WHERE conversation_id = ? AND user_id = ?",
               (get_current_time_str(), conv_id, uid))
    return jsonify({'success': True})


# --------------------------------------------------------------------------- #
# Notifications
# --------------------------------------------------------------------------- #
@mobile_bp.route('/notifications')
def notifications():
    uid = _uid()
    if not uid:
        return _unauth()
    rows = query_db("""SELECT id, notification_type, title, body, action_url, is_read, created_at
                       FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 50""", (uid,))
    return jsonify({'notifications': [{
        'id': r['id'], 'type': r['notification_type'], 'title': r['title'], 'body': r['body'],
        'action_url': r['action_url'], 'is_read': bool(r['is_read']), 'created_at': r['created_at'],
    } for r in rows]})


# --------------------------------------------------------------------------- #
# Campus wall
# --------------------------------------------------------------------------- #
@mobile_bp.route('/wall')
def wall():
    uid = _uid()
    if not uid:
        return _unauth()
    profile = query_db("SELECT university_id FROM user_profiles WHERE user_id = ?", (uid,), one=True)
    if not profile:
        return jsonify({'error': 'Profile required'}), 400

    scope = request.args.get('scope', 'my')
    category = request.args.get('category', 'all')
    sort = request.args.get('sort', 'recent')

    sql = """
        SELECT p.id, p.category, p.title, p.content, p.poll_options_json, p.likes_count,
               p.comments_count, p.is_pinned, p.created_at, p.user_id,
               u.display_name, u.primary_photo_path, univ.name AS university_name,
               (SELECT COUNT(*) FROM campus_wall_likes WHERE post_id = p.id AND user_id = ?) AS liked
        FROM campus_wall_posts p
        JOIN user_profiles u ON u.user_id = p.user_id
        JOIN universities univ ON univ.id = p.university_id
        WHERE 1=1"""
    params = [uid]
    if scope == 'my':
        sql += " AND p.university_id = ?"
        params.append(profile['university_id'])
    if category != 'all':
        sql += " AND p.category = ?"
        params.append(category)
    else:
        sql += " AND p.category NOT IN ('poll', 'event', 'activity')"
    sql += (" ORDER BY p.is_pinned DESC, p.likes_count DESC, p.id DESC" if sort == 'popular'
            else " ORDER BY p.is_pinned DESC, p.id DESC")
    sql += " LIMIT 50"

    posts = []
    for r in query_db(sql, tuple(params)):
        posts.append({
            'id': r['id'], 'category': r['category'], 'title': r['title'], 'content': r['content'],
            'likes_count': r['likes_count'], 'comments_count': r['comments_count'],
            'is_pinned': bool(r['is_pinned']), 'liked': bool(r['liked']),
            'created_at': r['created_at'], 'author_id': r['user_id'],
            'author_name': r['display_name'], 'author_photo': _photo(r['primary_photo_path']),
            'university_name': r['university_name'],
        })
    return jsonify({'posts': posts})


@mobile_bp.route('/wall/new', methods=['POST'])
def wall_new():
    uid = _uid()
    if not uid:
        return _unauth()
    profile = query_db("SELECT university_id FROM user_profiles WHERE user_id = ?", (uid,), one=True)
    if not profile:
        return jsonify({'error': 'Profile required'}), 400
    data = request.get_json(silent=True) or {}
    category = str(data.get('category', 'discussion')).strip()
    if category not in ('discussion', 'tip', 'lost_found'):
        category = 'discussion'
    title = str(data.get('title', '')).strip()[:255]
    content = str(data.get('content', '')).strip()[:5000]
    if not title or not content:
        return jsonify({'error': 'Title and content are required.'}), 400
    res = execute_db("""INSERT INTO campus_wall_posts (user_id, university_id, category, title, content, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)""",
                     (uid, profile['university_id'], category, title, content, get_current_time_str()))
    return jsonify({'success': True, 'post_id': res['lastrowid']})
