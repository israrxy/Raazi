/**
 * Cloudflare Pages Function: /api/sync
 * 
 * Verifies Google OIDC ID Token and performs read/write operations
 * against Azure Cosmos DB (raazi_db / users) with partition key "/id".
 */

const GOOGLE_CLIENT_ID = "974661534423-d8bo9cjl2a1tj3b1bnh7oe247qlcg1gt.apps.googleusercontent.com";
const DB_ID = "raazi_db";
const COLL_ID = "users";

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type",
};

export async function onRequestOptions() {
  return new Response(null, { status: 204, headers: CORS_HEADERS });
}

export async function onRequestGet(context) {
  return handleSync(context, "GET");
}

export async function onRequestPost(context) {
  return handleSync(context, "POST");
}

export async function onRequest(context) {
  const method = context.request.method.toUpperCase();
  if (method === "OPTIONS") return onRequestOptions();
  if (method === "GET") return onRequestGet(context);
  if (method === "POST") return onRequestPost(context);
  return jsonResponse({ error: "Method not allowed" }, 405);
}

async function handleSync(context, method) {
  const { request, env } = context;

  try {
    // 1. Verify Google Authorization header
    const authHeader = request.headers.get("Authorization") || "";
    if (!authHeader.startsWith("Bearer ")) {
      return jsonResponse({ error: "Missing Bearer token" }, 401);
    }
    const idToken = authHeader.substring(7).trim();

    const googleUser = await verifyGoogleIdToken(idToken);
    if (!googleUser) {
      return jsonResponse({ error: "Invalid Google ID token" }, 401);
    }

    const userId = googleUser.sub;
    const userEmail = googleUser.email;

    // 2. Parse Cosmos DB connection string
    const connStr = env.COSMOS_CONNECTION_STRING;
    if (!connStr) {
      return jsonResponse({ error: "Server misconfiguration: missing COSMOS_CONNECTION_STRING" }, 500);
    }
    const { endpoint, masterKey } = parseCosmosConnectionString(connStr);

    if (method === "GET") {
      // Fetch existing user doc
      const doc = await getCosmosDoc(endpoint, masterKey, userId);
      return jsonResponse(doc || { id: userId, email: userEmail, favorites: [], playlists: [] });
    }

    if (method === "POST") {
      const body = await request.json().catch(() => ({}));
      const incomingFavorites = Array.isArray(body.favorites) ? body.favorites : [];
      const incomingPlaylists = Array.isArray(body.playlists) ? body.playlists : [];
      const incomingLastFmKey = typeof body.lastfmSessionKey === "string" ? body.lastfmSessionKey : "";
      const incomingLastFmUser = typeof body.lastfmUsername === "string" ? body.lastfmUsername : "";

      // Fetch existing to merge
      const existingDoc = await getCosmosDoc(endpoint, masterKey, userId);

      const mergedFavorites = Array.from(new Set([
        ...(existingDoc?.favorites || []),
        ...incomingFavorites
      ]));

      // Merge playlists by id
      const playlistMap = new Map();
      (existingDoc?.playlists || []).forEach(p => p.id && playlistMap.set(p.id, p));
      incomingPlaylists.forEach(p => p.id && playlistMap.set(p.id, p));
      const mergedPlaylists = Array.from(playlistMap.values());

      const finalLastFmKey = incomingLastFmKey || existingDoc?.lastfmSessionKey || "";
      const finalLastFmUser = incomingLastFmUser || existingDoc?.lastfmUsername || "";

      const updatedDoc = {
        id: userId,
        email: userEmail,
        name: googleUser.name || "",
        picture: googleUser.picture || "",
        favorites: mergedFavorites,
        playlists: mergedPlaylists,
        lastfmSessionKey: finalLastFmKey,
        lastfmUsername: finalLastFmUser,
        updatedAt: Date.now()
      };

      await upsertCosmosDoc(endpoint, masterKey, updatedDoc);
      return jsonResponse(updatedDoc);
    }

    return jsonResponse({ error: "Method not allowed" }, 405);
  } catch (err) {
    return jsonResponse({ error: err.message || "Internal server error" }, 500);
  }
}

// --- Helper Functions ---

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json",
      ...CORS_HEADERS,
    },
  });
}

async function verifyGoogleIdToken(token) {
  try {
    const res = await fetch(`https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(token)}`);
    if (!res.ok) return null;
    const info = await res.json();
    if (info.aud !== GOOGLE_CLIENT_ID && !info.aud?.includes("974661534423")) {
      return null;
    }
    return info;
  } catch (_) {
    return null;
  }
}

function parseCosmosConnectionString(conn) {
  const parts = conn.split(";").reduce((acc, part) => {
    const idx = part.indexOf("=");
    if (idx > 0) {
      acc[part.substring(0, idx).trim()] = part.substring(idx + 1).trim();
    }
    return acc;
  }, {});

  const endpoint = parts["AccountEndpoint"] || "";
  const masterKey = parts["AccountKey"] || "";
  return { endpoint: endpoint.replace(/\/+$/, ""), masterKey };
}

async function generateCosmosAuth(verb, resourceType, resourceId, dateStr, masterKey) {
  const payload = `${verb.toLowerCase()}\n${resourceType.toLowerCase()}\n${resourceId}\n${dateStr.toLowerCase()}\n\n`;
  const keyBytes = Uint8Array.from(atob(masterKey), c => c.charCodeAt(0));
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    keyBytes,
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signatureBytes = await crypto.subtle.sign(
    "HMAC",
    cryptoKey,
    new TextEncoder().encode(payload)
  );
  const base64Sig = btoa(String.fromCharCode(...new Uint8Array(signatureBytes)));
  return `type=master&ver=1.0&sig=${encodeURIComponent(base64Sig)}`;
}

async function getCosmosDoc(endpoint, masterKey, docId) {
  const dateStr = new Date().toUTCString();
  const resourceLink = `dbs/${DB_ID}/colls/${COLL_ID}/docs/${docId}`;
  const auth = await generateCosmosAuth("GET", "docs", resourceLink, dateStr, masterKey);

  const res = await fetch(`${endpoint}/${resourceLink}`, {
    method: "GET",
    headers: {
      "x-ms-date": dateStr,
      "x-ms-version": "2018-12-31",
      "x-ms-documentdb-partitionkey": JSON.stringify([docId]),
      "Authorization": auth,
    },
  });

  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`Cosmos read error: ${res.status}`);
  return await res.json();
}

async function upsertCosmosDoc(endpoint, masterKey, doc) {
  const dateStr = new Date().toUTCString();
  const resourceLink = `dbs/${DB_ID}/colls/${COLL_ID}`;
  const auth = await generateCosmosAuth("POST", "docs", resourceLink, dateStr, masterKey);

  const res = await fetch(`${endpoint}/${resourceLink}/docs`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-ms-date": dateStr,
      "x-ms-version": "2018-12-31",
      "x-ms-documentdb-is-upsert": "true",
      "x-ms-documentdb-partitionkey": JSON.stringify([doc.id]),
      "Authorization": auth,
    },
  });

  if (!res.ok) {
    const errText = await res.text();
    throw new Error(`Cosmos upsert error: ${res.status} - ${errText}`);
  }
  return await res.json();
}
