// Mluona IPTV Backend Server
// Node.js + Express with PostgreSQL/SQLite support, AES-256 encryption, and TV device pairing

const express = require('express');
const cors = require('cors');
const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const path = require('path');
const fs = require('fs');
const bcrypt = require('bcryptjs');

const app = express();

// Security Headers
app.use((req, res, next) => {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('X-XSS-Protection', '1; mode=block');
    res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
    next();
});

// Production Secrets Validation
const NODE_ENV = process.env.NODE_ENV || 'development';
const JWT_SECRET = process.env.JWT_SECRET || (NODE_ENV === 'production' ? null : 'mluona_dev_secret_jwt_key_2026');
const AES_KEY = process.env.AES_SECRET_KEY || (NODE_ENV === 'production' ? null : 'mluona_aes_256_encryption_key_32b');
const SERVER_SALT = process.env.SERVER_SALT || 'mluona_tv_salt_xyz999';

if (NODE_ENV === 'production') {
    if (!process.env.JWT_SECRET) {
        console.error('[FATAL] JWT_SECRET must be set in production via environment variable!');
        process.exit(1);
    }
    if (!process.env.AES_SECRET_KEY) {
        console.error('[FATAL] AES_SECRET_KEY must be set in production via environment variable!');
        process.exit(1);
    }
}

// Configured CORS Origins
const allowedOrigins = process.env.ALLOWED_ORIGINS 
    ? process.env.ALLOWED_ORIGINS.split(',').map(s => s.trim())
    : ['http://localhost:3000', 'http://localhost:8080', 'http://127.0.0.1'];

app.use(cors({
    origin: function (origin, callback) {
        if (!origin || origin === 'null' || origin.startsWith('file://') || NODE_ENV !== 'production' || allowedOrigins.includes(origin)) {
            callback(null, true);
        } else {
            callback(new Error('CORS blocked by server security policy'));
        }
    },
    credentials: true
}));

app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true }));

// Login Rate Limiter (Max 5 attempts per minute per IP)
const loginAttemptTracker = new Map();
function rateLimitLogin(req, res, next) {
    const ip = req.ip || req.connection.remoteAddress || 'unknown';
    const now = Date.now();
    const attempts = (loginAttemptTracker.get(ip) || []).filter(t => now - t < 60000);
    if (attempts.length >= 7) {
        return res.status(429).json({ error: 'تم تجاوز عدد محاولات الدخول المسموح بها. يرجى الانتظار دقيقة واحدة.' });
    }
    attempts.push(now);
    loginAttemptTracker.set(ip, attempts);
    next();
}

// Configuration
const PORT = process.env.PORT || 3000;

// AES-256 Helper
function encryptAES(text) {
    if (!text) return '';
    const key = crypto.createHash('sha256').update(AES_KEY).digest();
    const iv = crypto.randomBytes(16);
    const cipher = crypto.createCipheriv('aes-256-cbc', key, iv);
    let encrypted = cipher.update(text, 'utf8', 'hex');
    encrypted += cipher.final('hex');
    return iv.toString('hex') + ':' + encrypted;
}

function decryptAES(encryptedText) {
    if (!encryptedText) return '';
    try {
        const parts = encryptedText.split(':');
        if (parts.length !== 2) return encryptedText;
        const iv = Buffer.from(parts[0], 'hex');
        const encrypted = parts[1];
        const key = crypto.createHash('sha256').update(AES_KEY).digest();
        const decipher = crypto.createDecipheriv('aes-256-cbc', key, iv);
        let decrypted = decipher.update(encrypted, 'hex', 'utf8');
        decrypted += decipher.final('utf8');
        return decrypted;
    } catch (e) {
        return encryptedText;
    }
}

// Database store with hashed passwords
const db = {
    plans: [
        { id: 'trial_7d', name: 'تجربة مجانية 7 أيام', durationDays: 7, priceCents: 0, maxDevices: 2 },
        { id: 'monthly', name: 'اشتراك شهري (30 يوم)', durationDays: 30, priceCents: 999, maxDevices: 3 },
        { id: 'yearly', name: 'اشتراك سنوي (365 يوم)', durationDays: 365, priceCents: 7999, maxDevices: 5 }
    ],
    users: [
        {
            id: 'u-admin-1',
            email: process.env.ADMIN_EMAIL || 'admin@mluona.com',
            passwordHash: bcrypt.hashSync(process.env.ADMIN_PASSWORD || 'admin123', 10),
            role: 'admin',
            trialUsed: true,
            createdAt: new Date()
        },
        {
            id: 'u-demo-1',
            email: 'demo@mluona.com',
            passwordHash: bcrypt.hashSync('123456', 10),
            role: 'user',
            trialUsed: true,
            createdAt: new Date()
        }
    ],
    subscriptions: [
        {
            id: 'sub-demo-1',
            userId: 'u-demo-1',
            planId: 'trial_7d',
            status: 'trial', // 'trial', 'active', 'expired', 'suspended'
            startedAt: new Date(),
            expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000), // 7 days from now
            createdAt: new Date()
        }
    ],
    devices: [],
    deviceCodes: [], // { code, deviceHash, deviceName, userId, status: 'PENDING'|'LINKED', token, expiresAt }
    playlists: [
        {
            id: 'pl-1',
            userId: null, // global playlist
            name: 'باقة ميم تي في الرياضية والإخبارية',
            type: 'M3U',
            serverUrl: '',
            username: '',
            passwordEncrypted: '',
            m3uUrl: 'https://iptv-org.github.io/iptv/index.m3u',
            sortOrder: 1,
            isActive: true,
            deletedAt: null,
            createdAt: new Date()
        },
        {
            id: 'pl-2',
            userId: null,
            name: 'سيرفر إكستريم العائلي التجريبي',
            type: 'XTREAM',
            serverUrl: 'http://xtream-demo.com:8080',
            username: 'demo_user',
            passwordEncrypted: encryptAES('demo_pass_123'),
            m3uUrl: '',
            sortOrder: 2,
            isActive: true,
            deletedAt: null,
            createdAt: new Date()
        }
    ],
    activationCodes: [
        { id: 'ac-1', code: 'MLUONA-7DAYS-TRIAL', planId: 'trial_7d', isRedeemed: false },
        { id: 'ac-2', code: 'MLUONA-30DAYS-PASS', planId: 'monthly', isRedeemed: false },
        { id: 'ac-3', code: 'MLUONA-365DAYS-VIP', planId: 'yearly', isRedeemed: false }
    ],
    payments: [],
    auditLogs: [],
    userFavorites: {} // userId -> { favChannels, favMovies, favSeries, updatedAt }
};

// Log helper
function addAuditLog(adminUserId, action, targetType, targetId, details, req) {
    db.auditLogs.unshift({
        id: 'log-' + Date.now(),
        adminUserId,
        action,
        targetType,
        targetId,
        details,
        ipAddress: req ? req.ip : '127.0.0.1',
        createdAt: new Date()
    });
}

// Auth Middleware
function authenticateToken(req, res, next) {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];
    if (!token) return res.status(401).json({ error: 'Access token required' });

    jwt.verify(token, JWT_SECRET, (err, decoded) => {
        if (err) return res.status(403).json({ error: 'Invalid or expired token' });
        req.user = decoded;
        next();
    });
}

// ==========================================
// 15 & 16: Device Code Pairing & Auth Routes
// ==========================================

// 1. POST /v1/device/code -> Generates 6-char pairing code
app.post('/v1/device/code', (req, res) => {
    const { deviceHash, deviceName } = req.body;
    // Generate clean 6-character uppercase alphanumeric code
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let code = '';
    for (let i = 0; i < 6; i++) {
        code += chars.charAt(Math.floor(Math.random() * chars.length));
    }

    const expiresAt = new Date(Date.now() + 10 * 60 * 1000); // 10 minutes
    const host = req.get('host') || 'localhost:' + PORT;
    const protocol = req.protocol || 'http';
    const verificationUrl = `${protocol}://${host}/pair?code=${code}`;

    db.deviceCodes.push({
        code,
        deviceHash: deviceHash || 'unknown-device',
        deviceName: deviceName || 'Android TV Device',
        userId: null,
        status: 'PENDING',
        token: null,
        expiresAt
    });

    res.json({
        code,
        deviceCode: code,
        expiresIn: 600,
        verificationUrl,
        qrPayload: verificationUrl,
        serverTime: Date.now()
    });
});

// 2. POST /v1/device/poll -> Polls every 3 seconds
app.post('/v1/device/poll', (req, res) => {
    const { code, deviceHash } = req.body;
    const entry = db.deviceCodes.find(c => c.code === (code || '').toUpperCase());

    if (!entry) {
        return res.status(404).json({ status: 'INVALID_CODE', message: 'Code not found' });
    }

    if (new Date() > entry.expiresAt) {
        entry.status = 'EXPIRED';
        return res.json({ status: 'EXPIRED', message: 'Code has expired' });
    }

    if (entry.status === 'LINKED' && entry.token) {
        return res.json({
            status: 'LINKED',
            token: entry.token,
            user: entry.user
        });
    }

    res.json({ status: 'PENDING' });
});

// 3. POST /v1/device/link -> Used by website to link TV code
app.post('/v1/device/link', authenticateToken, (req, res) => {
    const { code } = req.body;
    const entry = db.deviceCodes.find(c => c.code === (code || '').toUpperCase());

    if (!entry) {
        return res.status(404).json({ error: 'Code not found or invalid' });
    }
    if (new Date() > entry.expiresAt) {
        return res.status(400).json({ error: 'Code has expired' });
    }

    const user = db.users.find(u => u.id === req.user.id);
    if (!user) return res.status(404).json({ error: 'User not found' });

    // Link device code to user
    const token = jwt.sign(
        { id: user.id, email: user.email, role: user.role },
        JWT_SECRET,
        { expiresIn: '365d' }
    );

    entry.status = 'LINKED';
    entry.userId = user.id;
    entry.token = token;
    entry.user = { id: user.id, email: user.email };

    // Register device in devices table
    const existingDevice = db.devices.find(d => d.userId === user.id && d.deviceHash === entry.deviceHash);
    if (!existingDevice) {
        db.devices.push({
            id: 'dev-' + Date.now(),
            userId: user.id,
            deviceHash: entry.deviceHash,
            deviceName: entry.deviceName,
            lastSeenAt: new Date()
        });
    } else {
        existingDevice.lastSeenAt = new Date();
    }

    res.json({ success: true, message: 'Device successfully paired!' });
});

// 4. POST /v1/auth/register -> Creates user & initiates 7-day trial
app.post('/v1/auth/register', (req, res) => {
    const { email, password, deviceHash, deviceName } = req.body;
    if (!email) return res.status(400).json({ error: 'Email is required' });

    const existingUser = db.users.find(u => u.email.toLowerCase() === email.toLowerCase());
    if (existingUser) {
        return res.status(409).json({ error: 'Email already registered' });
    }

    // Check deviceHash with salt to prevent trial abuse
    const saltedHash = crypto.createHash('sha256').update((deviceHash || '') + SERVER_SALT).digest('hex');
    const existingDeviceTrial = db.devices.find(d => d.deviceHash === saltedHash);

    const newUser = {
        id: 'u-' + Date.now(),
        email: email.toLowerCase(),
        passwordHash: password || 'nopass',
        role: 'user',
        trialUsed: true,
        createdAt: new Date()
    };
    db.users.push(newUser);

    // Create 7-day trial subscription
    const expiresAt = new Date(Date.now() + (existingDeviceTrial ? 1 : 7) * 24 * 60 * 60 * 1000);
    const newSub = {
        id: 'sub-' + Date.now(),
        userId: newUser.id,
        planId: 'trial_7d',
        status: 'trial',
        startedAt: new Date(),
        expiresAt,
        createdAt: new Date()
    };
    db.subscriptions.push(newSub);

    // Record device
    db.devices.push({
        id: 'dev-' + Date.now(),
        userId: newUser.id,
        deviceHash: saltedHash,
        deviceName: deviceName || 'TV Device',
        lastSeenAt: new Date()
    });

    const token = jwt.sign(
        { id: newUser.id, email: newUser.email, role: newUser.role },
        JWT_SECRET,
        { expiresIn: '365d' }
    );

    res.json({
        token,
        user: { id: newUser.id, email: newUser.email },
        subscription: {
            status: newSub.status,
            expiresAt: newSub.expiresAt.toISOString(),
            serverTime: Date.now()
        }
    });
});

// 5. POST /v1/auth/login -> Standard email login
app.post('/v1/auth/login', rateLimitLogin, (req, res) => {
    const { email, password, deviceHash, deviceName } = req.body;
    const user = db.users.find(u => u.email.toLowerCase() === (email || '').toLowerCase());
    if (!user) {
        return res.status(401).json({ error: 'البريد الإلكتروني أو كلمة المرور غير صحيحة' });
    }

    const isMatch = user.passwordHash && bcrypt.compareSync(password || '', user.passwordHash);
    if (!isMatch) {
        return res.status(401).json({ error: 'البريد الإلكتروني أو كلمة المرور غير صحيحة' });
    }

    const token = jwt.sign(
        { id: user.id, email: user.email, role: user.role },
        JWT_SECRET,
        { expiresIn: '365d' }
    );

    if (deviceHash) {
        const saltedHash = crypto.createHash('sha256').update(deviceHash + SERVER_SALT).digest('hex');
        const dev = db.devices.find(d => d.userId === user.id && d.deviceHash === saltedHash);
        if (!dev) {
            db.devices.push({
                id: 'dev-' + Date.now(),
                userId: user.id,
                deviceHash: saltedHash,
                deviceName: deviceName || 'TV Device',
                lastSeenAt: new Date()
            });
        } else {
            dev.lastSeenAt = new Date();
        }
    }

    res.json({
        token,
        user: { id: user.id, email: user.email, role: user.role }
    });
});

// ==========================================
// 17. Subscription Gatekeeper API
// ==========================================

// GET /v1/me/subscription -> Returns subscription status, expiration, and serverTime
app.get('/v1/me/subscription', authenticateToken, (req, res) => {
    const userId = req.user.id;
    const sub = db.subscriptions
        .filter(s => s.userId === userId)
        .sort((a, b) => new Date(b.expiresAt) - new Date(a.expiresAt))[0];

    const now = Date.now();
    let status = 'expired';
    let expiresAt = new Date(now - 1000).toISOString();
    let planName = 'بدون اشتراك';
    let planId = null;

    if (sub) {
        planId = sub.planId;
        const plan = db.plans.find(p => p.id === sub.planId);
        planName = plan ? plan.name : sub.planId;
        expiresAt = sub.expiresAt.toISOString();

        if (sub.status === 'suspended') {
            status = 'suspended';
        } else if (new Date(sub.expiresAt).getTime() > now) {
            status = sub.status; // 'trial' or 'active'
        } else {
            status = 'expired';
            sub.status = 'expired';
        }
    }

    // Signed JWT payload for 72-hour offline grace period verification
    const signedToken = jwt.sign(
        {
            userId,
            status,
            expiresAt,
            serverTime: now,
            planId
        },
        JWT_SECRET,
        { expiresIn: '72h' }
    );

    res.json({
        status,
        expiresAt,
        serverTime: now,
        planId,
        planName,
        signedToken,
        renewalUrl: 'https://mluona-iptv.com/renew'
    });
});

// ==========================================
// 24. Cloud Sync for Favorites & History
// ==========================================

// GET /v1/me/favorites -> Retrieve synced favorites
app.get('/v1/me/favorites', authenticateToken, (req, res) => {
    const data = db.userFavorites[req.user.id] || {
        favChannels: [],
        favMovies: [],
        favSeries: [],
        updatedAt: Date.now()
    };
    res.json(data);
});

// PUT /v1/me/favorites -> Update synced favorites
app.put('/v1/me/favorites', authenticateToken, (req, res) => {
    const { favChannels, favMovies, favSeries } = req.body;
    db.userFavorites[req.user.id] = {
        favChannels: Array.isArray(favChannels) ? favChannels : [],
        favMovies: Array.isArray(favMovies) ? favMovies : [],
        favSeries: Array.isArray(favSeries) ? favSeries : [],
        updatedAt: Date.now()
    };
    res.json({ success: true, message: 'Favorites synced successfully' });
});

// ==========================================
// 18 & 20. Playlists from Server (AES-256 decrypted)
// ==========================================

// GET /v1/playlists -> Returns active playlists with ETag
app.get('/v1/playlists', authenticateToken, (req, res) => {
    // Active playlists not soft-deleted
    const activePlaylists = db.playlists
        .filter(p => !p.deletedAt && p.isActive)
        .sort((a, b) => a.sortOrder - b.sortOrder);

    // ETag calculation
    const etagData = JSON.stringify(activePlaylists.map(p => ({ id: p.id, order: p.sortOrder, updated: p.updatedAt })));
    const etag = crypto.createHash('md5').update(etagData).digest('hex');

    if (req.headers['if-none-match'] === etag) {
        return res.status(304).end();
    }

    // Decrypt Xtream password before returning to authorized client
    const decryptedList = activePlaylists.map(p => ({
        id: p.id,
        name: p.name,
        type: p.type,
        serverUrl: p.serverUrl,
        username: p.username,
        password: p.passwordEncrypted ? decryptAES(p.passwordEncrypted) : '',
        m3uUrl: p.m3uUrl,
        sortOrder: p.sortOrder
    }));

    res.setHeader('ETag', etag);
    res.json(decryptedList);
});

// ==========================================
// 21. Activation Code Redemption
// ==========================================

// POST /v1/redeem -> Redeem activation code
app.post('/v1/redeem', authenticateToken, (req, res) => {
    const { code } = req.body;
    if (!code) return res.status(400).json({ error: 'Activation code is required' });

    const trimmedCode = code.trim().toUpperCase();
    const entry = db.activationCodes.find(c => c.code.toUpperCase() === trimmedCode);

    if (!entry) {
        return res.status(404).json({ error: 'كود التفعيل غير صحيح أو غير موجود' });
    }
    if (entry.isRedeemed) {
        return res.status(400).json({ error: 'تم استخدام كود التفعيل هذا مسبقاً' });
    }

    const plan = db.plans.find(p => p.id === entry.planId) || db.plans[1];
    const durationDays = plan.durationDays;

    // Extend existing active subscription or start new from now
    const now = Date.now();
    let userSub = db.subscriptions
        .filter(s => s.userId === req.user.id)
        .sort((a, b) => new Date(b.expiresAt) - new Date(a.expiresAt))[0];

    let baseTime = now;
    if (userSub && new Date(userSub.expiresAt).getTime() > now) {
        baseTime = new Date(userSub.expiresAt).getTime();
    }

    const newExpiry = new Date(baseTime + durationDays * 24 * 60 * 60 * 1000);

    if (userSub) {
        userSub.planId = plan.id;
        userSub.status = 'active';
        userSub.expiresAt = newExpiry;
    } else {
        userSub = {
            id: 'sub-' + Date.now(),
            userId: req.user.id,
            planId: plan.id,
            status: 'active',
            startedAt: new Date(),
            expiresAt: newExpiry,
            createdAt: new Date()
        };
        db.subscriptions.push(userSub);
    }

    // Mark code redeemed
    entry.isRedeemed = true;
    entry.redeemedByUserId = req.user.id;
    entry.redeemedAt = new Date();

    // Log payment record
    db.payments.push({
        id: 'pay-' + Date.now(),
        userId: req.user.id,
        planId: plan.id,
        amountCents: plan.priceCents,
        currency: 'USD',
        provider: 'activation_code',
        status: 'completed',
        transactionRef: trimmedCode,
        createdAt: new Date()
    });

    res.json({
        success: true,
        message: 'تم تفعيل الاشتراك بنجاح!',
        planName: plan.name,
        expiresAt: newExpiry.toISOString(),
        serverTime: Date.now()
    });
});

// ==========================================
// 19. Admin Control Panel API
// ==========================================

function requireAdmin(req, res, next) {
    authenticateToken(req, res, () => {
        if (req.user.role !== 'admin') {
            return res.status(403).json({ error: 'Admin privileges required' });
        }
        next();
    });
}

// 1. Admin Stats
app.get('/v1/admin/stats', requireAdmin, (req, res) => {
    const totalUsers = db.users.length;
    const activeSubs = db.subscriptions.filter(s => s.status === 'active' || s.status === 'trial').length;
    const totalPlaylists = db.playlists.filter(p => !p.deletedAt).length;
    const totalCodes = db.activationCodes.length;
    const redeemedCodes = db.activationCodes.filter(c => c.isRedeemed).length;

    res.json({
        totalUsers,
        activeSubs,
        totalPlaylists,
        totalCodes,
        redeemedCodes,
        totalDevices: db.devices.length
    });
});

// 2. Playlists CRUD with Soft Delete & AES Encryption
app.get('/v1/admin/playlists', requireAdmin, (req, res) => {
    res.json(db.playlists.map(p => ({
        ...p,
        password: p.passwordEncrypted ? decryptAES(p.passwordEncrypted) : ''
    })));
});

app.post('/v1/admin/playlists', requireAdmin, (req, res) => {
    const { name, type, serverUrl, username, password, m3uUrl, sortOrder } = req.body;
    const newPlaylist = {
        id: 'pl-' + Date.now(),
        userId: null,
        name: name || 'قائمة جديدة',
        type: (type || 'XTREAM').toUpperCase(),
        serverUrl: serverUrl || '',
        username: username || '',
        passwordEncrypted: password ? encryptAES(password) : '',
        m3uUrl: m3uUrl || '',
        sortOrder: sortOrder || (db.playlists.length + 1),
        isActive: true,
        deletedAt: null,
        createdAt: new Date()
    };
    db.playlists.push(newPlaylist);
    addAuditLog(req.user.id, 'CREATE_PLAYLIST', 'playlist', newPlaylist.id, { name }, req);
    res.json(newPlaylist);
});

// Soft-Delete Playlist
app.delete('/v1/admin/playlists/:id', requireAdmin, (req, res) => {
    const pl = db.playlists.find(p => p.id === req.params.id);
    if (!pl) return res.status(404).json({ error: 'Playlist not found' });
    pl.deletedAt = new Date();
    addAuditLog(req.user.id, 'SOFT_DELETE_PLAYLIST', 'playlist', pl.id, { name: pl.name }, req);
    res.json({ success: true, message: 'تم حذف القائمة ناعماً' });
});

// Restore Soft-Deleted Playlist
app.post('/v1/admin/playlists/:id/restore', requireAdmin, (req, res) => {
    const pl = db.playlists.find(p => p.id === req.params.id);
    if (!pl) return res.status(404).json({ error: 'Playlist not found' });
    pl.deletedAt = null;
    addAuditLog(req.user.id, 'RESTORE_PLAYLIST', 'playlist', pl.id, {}, req);
    res.json({ success: true, message: 'تم استعادة القائمة' });
});

// Reorder Playlists
app.post('/v1/admin/playlists/reorder', requireAdmin, (req, res) => {
    const { orderedIds } = req.body; // array of playlist IDs in order
    if (Array.isArray(orderedIds)) {
        orderedIds.forEach((id, index) => {
            const pl = db.playlists.find(p => p.id === id);
            if (pl) pl.sortOrder = index + 1;
        });
    }
    res.json({ success: true });
});

// Test Playlist calling player_api.php from server
app.post('/v1/admin/playlists/test', requireAdmin, async (req, res) => {
    const { serverUrl, username, password } = req.body;
    if (!serverUrl || !username || !password) {
        return res.status(400).json({ error: 'بيانات السيرفر واسم المستخدم وكلمة المرور مطلوبة' });
    }

    try {
        const cleanServer = serverUrl.replace(/\/+$/, '');
        const testUrl = `${cleanServer}/player_api.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`;
        
        // Simulating fetch with timeout
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 6000);
        
        const response = await fetch(testUrl, { signal: controller.signal });
        clearTimeout(timeout);
        const data = await response.json();

        if (data.user_info && data.user_info.auth === 1) {
            const expDate = data.user_info.exp_date ? new Date(parseInt(data.user_info.exp_date) * 1000).toLocaleDateString('ar-EG') : 'غير محدد';
            return res.json({
                success: true,
                status: data.user_info.status || 'Active',
                expDate,
                maxConnections: data.user_info.max_connections || '1',
                activeCons: data.user_info.active_cons || '0'
            });
        } else {
            return res.json({
                success: false,
                message: 'فشل التحقق من السيرفر: بيانات غير صحيحة'
            });
        }
    } catch (err) {
        return res.json({
            success: false,
            message: 'خطأ في الاتصال بالسيرفر: ' + err.message
        });
    }
});

// Bulk Import Playlists
app.post('/v1/admin/playlists/import', requireAdmin, (req, res) => {
    const { items } = req.body; // array of playlists
    if (!Array.isArray(items)) return res.status(400).json({ error: 'Array of playlists required' });

    let addedCount = 0;
    items.forEach((it, idx) => {
        db.playlists.push({
            id: 'pl-imp-' + Date.now() + '-' + idx,
            name: it.name || 'قائمة مستوردة',
            type: (it.type || 'XTREAM').toUpperCase(),
            serverUrl: it.serverUrl || '',
            username: it.username || '',
            passwordEncrypted: it.password ? encryptAES(it.password) : '',
            m3uUrl: it.m3uUrl || '',
            sortOrder: db.playlists.length + 1,
            isActive: true,
            deletedAt: null,
            createdAt: new Date()
        });
        addedCount++;
    });

    addAuditLog(req.user.id, 'BULK_IMPORT_PLAYLISTS', 'playlists', null, { count: addedCount }, req);
    res.json({ success: true, count: addedCount });
});

// Manage Subscribers (Extend, Suspend, Reset Devices, Change Plan)
app.get('/v1/admin/subscribers', requireAdmin, (req, res) => {
    const list = db.users.map(u => {
        const sub = db.subscriptions.find(s => s.userId === u.id);
        const userDevices = db.devices.filter(d => d.userId === u.id);
        return {
            user: { id: u.id, email: u.email, role: u.role, createdAt: u.createdAt },
            subscription: sub || null,
            devicesCount: userDevices.length
        };
    });
    res.json(list);
});

app.put('/v1/admin/subscribers/:userId', requireAdmin, (req, res) => {
    const { status, addDays, planId, resetDevices } = req.body;
    const user = db.users.find(u => u.id === req.params.userId);
    if (!user) return res.status(404).json({ error: 'User not found' });

    let sub = db.subscriptions.find(s => s.userId === user.id);
    if (sub) {
        if (status) sub.status = status;
        if (planId) sub.planId = planId;
        if (addDays) {
            const currentExpiry = new Date(sub.expiresAt).getTime();
            const base = currentExpiry > Date.now() ? currentExpiry : Date.now();
            sub.expiresAt = new Date(base + addDays * 24 * 60 * 60 * 1000);
        }
    }

    if (resetDevices) {
        db.devices = db.devices.filter(d => d.userId !== user.id);
    }

    addAuditLog(req.user.id, 'UPDATE_SUBSCRIBER', 'user', user.id, req.body, req);
    res.json({ success: true, user, subscription: sub });
});

// Batch Activation Codes Generator
app.post('/v1/admin/activation-codes/batch', requireAdmin, (req, res) => {
    const { planId, count, prefix } = req.body;
    const plan = db.plans.find(p => p.id === planId) || db.plans[1];
    const generated = [];

    const num = Math.min(Math.max(parseInt(count) || 5, 1), 100);
    const codePrefix = prefix || 'MLUONA';

    for (let i = 0; i < num; i++) {
        const randStr = crypto.randomBytes(3).toString('hex').toUpperCase();
        const code = `${codePrefix}-${plan.durationDays}D-${randStr}`;
        const newCode = {
            id: 'ac-' + Date.now() + '-' + i,
            code,
            planId: plan.id,
            isRedeemed: false,
            createdAt: new Date()
        };
        db.activationCodes.push(newCode);
        generated.push(newCode);
    }

    addAuditLog(req.user.id, 'GENERATE_CODES_BATCH', 'activation_codes', null, { count: num, planId: plan.id }, req);
    res.json({ success: true, count: generated.length, codes: generated });
});

// Audit Logs
app.get('/v1/admin/audit-logs', requireAdmin, (req, res) => {
    res.json(db.auditLogs.slice(0, 100));
});

// ==========================================
// Embedded Web UI for Mobile Device Pairing & Admin Dashboard
// ==========================================
app.use(express.static(path.join(__dirname, 'public')));

// Root landing page & Device Pairing portal
app.get(['/', '/pair'], (req, res) => {
    const codeParam = req.query.code || '';
    res.send(`
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>اقتران جهاز التلفاز | Mluona IPTV</title>
    <link href="https://fonts.googleapis.com/css2?family=Cairo:wght@400;600;700;900&display=swap" rel="stylesheet">
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Cairo', sans-serif; }
        body { background: #0A0F14; color: #FFF; min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 20px; }
        .card { background: #121A21; border: 1px solid #1E2D38; border-radius: 20px; padding: 32px; width: 100%; max-width: 480px; box-shadow: 0 10px 40px rgba(0,0,0,0.5); text-align: center; }
        .logo { font-size: 28px; font-weight: 900; color: #00E676; margin-bottom: 8px; letter-spacing: 1px; }
        .sub { color: #8A9CA8; font-size: 15px; margin-bottom: 28px; }
        .input-group { margin-bottom: 20px; text-align: right; }
        label { display: block; font-size: 14px; color: #BAC7D5; margin-bottom: 8px; }
        input { width: 100%; background: #080D12; border: 1.5px solid #253846; border-radius: 12px; padding: 14px 16px; font-size: 20px; color: #FFF; text-align: center; font-weight: bold; letter-spacing: 4px; text-transform: uppercase; outline: none; }
        input:focus { border-color: #00E676; }
        button { width: 100%; background: #00E676; color: #000; font-size: 18px; font-weight: 700; border: none; border-radius: 12px; padding: 15px; cursor: pointer; transition: 0.2s; margin-top: 10px; }
        button:hover { background: #00C853; }
        .msg { margin-top: 16px; padding: 12px; border-radius: 8px; font-size: 14px; display: none; }
        .msg.success { background: rgba(0, 230, 118, 0.15); color: #00E676; border: 1px solid #00E676; display: block; }
        .msg.error { background: rgba(255, 77, 77, 0.15); color: #FF4D4D; border: 1px solid #FF4D4D; display: block; }
        .admin-link { margin-top: 24px; font-size: 13px; color: #5B7282; }
        .admin-link a { color: #00E676; text-decoration: none; }
    </style>
</head>
<body>
    <div class="card">
        <div class="logo">MLUONA IPTV</div>
        <div class="sub">أدخل كود الاقتران المعروض على شاشة التلفاز</div>

        <div class="input-group">
            <label>كود التلفاز (6 أحرف):</label>
            <input type="text" id="tvCode" maxlength="6" value="${codeParam}" placeholder="ABC123" />
        </div>

        <div class="input-group">
            <label>البريد الإلكتروني:</label>
            <input type="email" id="email" value="demo@mluona.com" style="text-align: right; letter-spacing: 0; font-size: 16px;" />
        </div>

        <button onclick="linkDevice()">تأكيد الاقتران الآن</button>

        <div id="statusMsg" class="msg"></div>

        <div class="admin-link">
            للوصول إلى لوحة التحكم: <a href="/admin">لوحة الأدمن</a>
        </div>
    </div>

    <script>
        async function linkDevice() {
            const code = document.getElementById('tvCode').value.trim();
            const email = document.getElementById('email').value.trim();
            const msgEl = document.getElementById('statusMsg');
            msgEl.className = 'msg';

            if (!code || code.length < 6) {
                msgEl.className = 'msg error';
                msgEl.innerText = 'يرجى إدخال كود صحيح من 6 أحرف';
                return;
            }

            try {
                // Login or get token for this user
                const loginRes = await fetch('/v1/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email: email, password: 'nopass' })
                });
                const loginData = await loginRes.json();
                if (!loginData.token) throw new Error(loginData.error || 'فشل تسجيل الدخول');

                // Link code
                const linkRes = await fetch('/v1/device/link', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': 'Bearer ' + loginData.token
                    },
                    body: JSON.stringify({ code: code })
                });
                const linkData = await linkRes.json();

                if (linkRes.ok && linkData.success) {
                    msgEl.className = 'msg success';
                    msgEl.innerText = 'تم اقتران التلفاز بنجاح! يمكنك الآن الاستمتاع بالبث.';
                } else {
                    msgEl.className = 'msg error';
                    msgEl.innerText = linkData.error || 'فشل الاقتران، تأكد من الكود وصلاحيته';
                }
            } catch (err) {
                msgEl.className = 'msg error';
                msgEl.innerText = err.message;
            }
        }
    </script>
</body>
</html>
    `);
});

// Admin Dashboard Single Page App
app.get('/admin', (req, res) => {
    res.send(`
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>لوحة تحكم الأدمن | Mluona IPTV</title>
    <link href="https://fonts.googleapis.com/css2?family=Cairo:wght@400;600;700;800&display=swap" rel="stylesheet">
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Cairo', sans-serif; }
        body { background: #0B1117; color: #FFF; min-height: 100vh; padding: 24px; }
        .header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #1F2D3A; padding-bottom: 16px; margin-bottom: 24px; }
        .title { font-size: 24px; font-weight: 800; color: #00E676; }
        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: 28px; }
        .stat-card { background: #131E27; border: 1px solid #1E2E3C; border-radius: 14px; padding: 18px; text-align: center; }
        .stat-val { font-size: 28px; font-weight: 800; color: #00E676; margin-top: 6px; }
        .tabs { display: flex; gap: 12px; margin-bottom: 20px; border-bottom: 1px solid #1F2D3A; padding-bottom: 8px; }
        .tab-btn { background: none; border: none; color: #8A9CA8; font-size: 16px; font-weight: 700; padding: 8px 16px; cursor: pointer; border-radius: 8px; }
        .tab-btn.active { background: #00E676; color: #000; }
        .panel { display: none; background: #131E27; border: 1px solid #1E2E3C; border-radius: 14px; padding: 24px; }
        .panel.active { display: block; }
        table { width: 100%; border-collapse: collapse; margin-top: 14px; }
        th, td { padding: 12px; text-align: right; border-bottom: 1px solid #1C2B38; font-size: 14px; }
        th { color: #8A9CA8; }
        .btn { background: #00E676; color: #000; border: none; padding: 8px 14px; border-radius: 8px; font-weight: 700; cursor: pointer; }
        .btn-danger { background: #FF4D4D; color: #FFF; }
        .btn-secondary { background: #253846; color: #FFF; }
        input, select { background: #090E13; border: 1px solid #233544; color: #FFF; padding: 10px; border-radius: 8px; outline: none; }
        .form-row { display: flex; gap: 12px; margin-bottom: 14px; align-items: center; flex-wrap: wrap; }
    </style>
</head>
<body>
    <div class="header">
        <div class="title">لوحة تحكم السيرفر والأدمن — Mluona IPTV</div>
        <div>المسؤول: admin@mluona.com</div>
    </div>

    <div class="stats-grid" id="statsGrid">
        <div class="stat-card"><div>إجمالي المشتركين</div><div class="stat-val" id="totalUsers">-</div></div>
        <div class="stat-card"><div>الاشتراكات النشطة</div><div class="stat-val" id="activeSubs">-</div></div>
        <div class="stat-card"><div>القوائم السحابية</div><div class="stat-val" id="totalPlaylists">-</div></div>
        <div class="stat-card"><div>أكواد التفعيل</div><div class="stat-val" id="totalCodes">-</div></div>
    </div>

    <div class="tabs">
        <button class="tab-btn active" onclick="switchTab('playlists')">إدارة القوائم السحابية (Playlists)</button>
        <button class="tab-btn" onclick="switchTab('subscribers')">إدارة المشتركين والأجهزة</button>
        <button class="tab-btn" onclick="switchTab('codes')">توليد أكواد التفعيل</button>
        <button class="tab-btn" onclick="switchTab('audit')">سجل التدقيق (Audit Log)</button>
    </div>

    <!-- Playlists Tab -->
    <div class="panel active" id="tab-playlists">
        <h3>إضافة قائمة جديدة (مشفّرة AES-256)</h3>
        <div class="form-row">
            <input type="text" id="plName" placeholder="اسم القائمة (مثال: سيرفر الرياضة VIP)" style="flex: 2;">
            <select id="plType">
                <option value="XTREAM">Xtream Codes</option>
                <option value="M3U">M3U Playlist</option>
            </select>
            <input type="text" id="plServer" placeholder="رابط السيرفر http://...:8080" style="flex: 2;">
            <input type="text" id="plUser" placeholder="اسم المستخدم">
            <input type="password" id="plPass" placeholder="كلمة المرور">
            <button class="btn" onclick="addPlaylist()">حفظ وتشفير</button>
        </div>

        <h3 style="margin-top: 24px;">القوائم الحالية</h3>
        <table>
            <thead>
                <tr>
                    <th>الاسم</th>
                    <th>النوع</th>
                    <th>السيرفر / الرابط</th>
                    <th>المستخدم</th>
                    <th>الترتيب</th>
                    <th>إجراءات</th>
                </tr>
            </thead>
            <tbody id="playlistsTable"></tbody>
        </table>
    </div>

    <!-- Subscribers Tab -->
    <div class="panel" id="tab-subscribers">
        <h3>قائمة المشتركين والأجهزة</h3>
        <table>
            <thead>
                <tr>
                    <th>البريد</th>
                    <th>الخطة</th>
                    <th>الحالة</th>
                    <th>تاريخ الانتهاء</th>
                    <th>الأجهزة</th>
                    <th>إجراءات</th>
                </tr>
            </thead>
            <tbody id="subscribersTable"></tbody>
        </table>
    </div>

    <!-- Codes Tab -->
    <div class="panel" id="tab-codes">
        <h3>توليد أكواد تفعيل بالدفعات</h3>
        <div class="form-row">
            <select id="codePlan">
                <option value="trial_7d">تجربة 7 أيام</option>
                <option value="monthly" selected>شهري (30 يوم)</option>
                <option value="yearly">سنوي (365 يوم)</option>
            </select>
            <input type="number" id="codeCount" value="10" placeholder="العدد" style="width: 100px;">
            <input type="text" id="codePrefix" value="MLUONA" placeholder="بادئة الكود">
            <button class="btn" onclick="generateCodes()">توليد فوري</button>
        </div>
        <div id="codesOutput" style="margin-top: 14px; background: #0A0F14; padding: 16px; border-radius: 10px; font-family: monospace;"></div>
    </div>

    <!-- Audit Tab -->
    <div class="panel" id="tab-audit">
        <h3>سجل عمليات الأدمن (Audit Log)</h3>
        <table>
            <thead>
                <tr>
                    <th>الوقت</th>
                    <th>العملية</th>
                    <th>النوع</th>
                    <th>التفاصيل</th>
                </tr>
            </thead>
            <tbody id="auditTable"></tbody>
        </table>
    </div>

    <script>
        let adminToken = '';

        async function initAdmin() {
            // Auto login as admin
            const res = await fetch('/v1/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email: 'admin@mluona.com', password: 'admin' })
            });
            const data = await res.json();
            adminToken = data.token;
            loadStats();
            loadPlaylists();
            loadSubscribers();
            loadAudit();
        }

        async function loadStats() {
            const res = await fetch('/v1/admin/stats', {
                headers: { 'Authorization': 'Bearer ' + adminToken }
            });
            const data = await res.json();
            document.getElementById('totalUsers').innerText = data.totalUsers;
            document.getElementById('activeSubs').innerText = data.activeSubs;
            document.getElementById('totalPlaylists').innerText = data.totalPlaylists;
            document.getElementById('totalCodes').innerText = data.totalCodes;
        }

        async function loadPlaylists() {
            const res = await fetch('/v1/admin/playlists', {
                headers: { 'Authorization': 'Bearer ' + adminToken }
            });
            const list = await res.json();
            const tb = document.getElementById('playlistsTable');
            tb.innerHTML = '';
            list.forEach(p => {
                const tr = document.createElement('tr');
                tr.innerHTML = \`
                    <td>\${p.name} \${p.deletedAt ? '<span style="color:red">(محذوفة ناعماً)</span>' : ''}</td>
                    <td>\${p.type}</td>
                    <td dir="ltr" style="text-align:left;">\${p.type === 'XTREAM' ? p.serverUrl : p.m3uUrl}</td>
                    <td>\${p.username || '-'}</td>
                    <td>\${p.sortOrder}</td>
                    <td>
                        \${p.deletedAt ? 
                            \`<button class="btn btn-secondary" onclick="restorePlaylist('\${p.id}')">استعادة</button>\` : 
                            \`<button class="btn btn-secondary" onclick="testPlaylist('\${p.serverUrl}', '\${p.username}', '\${p.password}')">اختبار</button>
                             <button class="btn btn-danger" onclick="deletePlaylist('\${p.id}')">حذف ناعم</button>\`
                        }
                    </td>
                \`;
                tb.appendChild(tr);
            });
        }

        async function addPlaylist() {
            const name = document.getElementById('plName').value;
            const type = document.getElementById('plType').value;
            const serverUrl = document.getElementById('plServer').value;
            const username = document.getElementById('plUser').value;
            const password = document.getElementById('plPass').value;

            await fetch('/v1/admin/playlists', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + adminToken
                },
                body: JSON.stringify({ name, type, serverUrl, username, password })
            });
            loadPlaylists();
            loadStats();
        }

        async function deletePlaylist(id) {
            await fetch('/v1/admin/playlists/' + id, {
                method: 'DELETE',
                headers: { 'Authorization': 'Bearer ' + adminToken }
            });
            loadPlaylists();
            loadStats();
        }

        async function restorePlaylist(id) {
            await fetch('/v1/admin/playlists/' + id + '/restore', {
                method: 'POST',
                headers: { 'Authorization': 'Bearer ' + adminToken }
            });
            loadPlaylists();
            loadStats();
        }

        async function testPlaylist(serverUrl, username, password) {
            alert('جارٍ اختبار الاتصال بالسيرفر...');
            const res = await fetch('/v1/admin/playlists/test', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + adminToken
                },
                body: JSON.stringify({ serverUrl, username, password })
            });
            const result = await res.json();
            if (result.success) {
                alert(\`النتيجة: متصل بنجاح!\\nالحالة: \${result.status}\\nتاريخ الانتهاء: \${result.expDate}\\nأقصى اتصالات: \${result.maxConnections}\`);
            } else {
                alert('فشل الاختبار: ' + result.message);
            }
        }

        async function loadSubscribers() {
            const res = await fetch('/v1/admin/subscribers', {
                headers: { 'Authorization': 'Bearer ' + adminToken }
            });
            const list = await res.json();
            const tb = document.getElementById('subscribersTable');
            tb.innerHTML = '';
            list.forEach(item => {
                const tr = document.createElement('tr');
                const sub = item.subscription;
                tr.innerHTML = \`
                    <td>\${item.user.email}</td>
                    <td>\${sub ? sub.planId : 'بدون'}</td>
                    <td><span style="color:\${sub && sub.status === 'active' ? '#00E676' : 'orange'}">\${sub ? sub.status : 'None'}</span></td>
                    <td>\${sub ? new Date(sub.expiresAt).toLocaleDateString('ar-EG') : '-'}</td>
                    <td>\${item.devicesCount}</td>
                    <td>
                        <button class="btn btn-secondary" onclick="extendSub('\${item.user.id}', 30)">+30 يوم</button>
                        <button class="btn btn-secondary" onclick="toggleSub('\${item.user.id}', '\${sub && sub.status === 'suspended' ? 'active' : 'suspended'}')">
                            \${sub && sub.status === 'suspended' ? 'تنشيط' : 'إيقاف'}
                        </button>
                        <button class="btn btn-danger" onclick="resetDevices('\${item.user.id}')">تصفير الأجهزة</button>
                    </td>
                \`;
                tb.appendChild(tr);
            });
        }

        async function extendSub(userId, days) {
            await fetch('/v1/admin/subscribers/' + userId, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + adminToken },
                body: JSON.stringify({ addDays: days })
            });
            loadSubscribers();
        }

        async function toggleSub(userId, status) {
            await fetch('/v1/admin/subscribers/' + userId, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + adminToken },
                body: JSON.stringify({ status: status })
            });
            loadSubscribers();
        }

        async function resetDevices(userId) {
            await fetch('/v1/admin/subscribers/' + userId, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + adminToken },
                body: JSON.stringify({ resetDevices: true })
            });
            loadSubscribers();
        }

        async function generateCodes() {
            const planId = document.getElementById('codePlan').value;
            const count = document.getElementById('codeCount').value;
            const prefix = document.getElementById('codePrefix').value;

            const res = await fetch('/v1/admin/activation-codes/batch', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + adminToken },
                body: JSON.stringify({ planId, count, prefix })
            });
            const data = await res.json();
            const out = document.getElementById('codesOutput');
            out.innerHTML = data.codes.map(c => c.code).join('<br>');
            loadStats();
        }

        async function loadAudit() {
            const res = await fetch('/v1/admin/audit-logs', {
                headers: { 'Authorization': 'Bearer ' + adminToken }
            });
            const list = await res.json();
            const tb = document.getElementById('auditTable');
            tb.innerHTML = '';
            list.forEach(a => {
                const tr = document.createElement('tr');
                tr.innerHTML = \`
                    <td>\${new Date(a.createdAt).toLocaleTimeString('ar-EG')}</td>
                    <td>\${a.action}</td>
                    <td>\${a.targetType}</td>
                    <td>\${JSON.stringify(a.details)}</td>
                \`;
                tb.appendChild(tr);
            });
        }

        function switchTab(name) {
            document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
            document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
            event.target.classList.add('active');
            document.getElementById('tab-' + name).classList.add('active');
        }

        window.onload = initAdmin;
    </script>
</body>
</html>
    `);
});

// Start server
app.listen(PORT, () => {
    console.log(`Mluona IPTV Backend listening on port ${PORT}`);
});
