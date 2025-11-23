/**
 * API Utility Layer
 *
 * Centralized API communication with:
 * - Consistent error handling
 * - Type documentation via JSDoc
 * - Reusable request logic
 * - Support for BOTH Java and Python backends
 */

/**
 * Backend Configuration
 *
 * Set REACT_APP_BACKEND environment variable to choose backend:
 * - 'java' → Java Spring Boot backend (port 8080) - DEFAULT
 * - 'python' → Python FastAPI backend (port 8000)
 *
 * Both backends use the same RESTful endpoints (e.g., /api/users/login)
 *
 * To switch backends:
 * export REACT_APP_BACKEND=python && npm start
 * or
 * REACT_APP_BACKEND=python npm start
 */
const BACKEND = process.env.REACT_APP_BACKEND || 'java';

// Backend configuration - uses absolute URLs for both Java and Python backends
// Switch backends using environment variable:
//   REACT_APP_BACKEND=java npm start   (default - port 8080)
//   REACT_APP_BACKEND=python npm start (port 8000)
const API_BASE_URL = BACKEND === 'python'
  ? 'http://localhost:8000'   // Python FastAPI backend
  : 'http://localhost:8080';  // Java Spring Boot backend

console.log(`🔌 Using ${BACKEND.toUpperCase()} backend at ${API_BASE_URL}`);

// API Endpoints - RESTful paths that work with BOTH backends
export const API_ENDPOINTS = {
  LOGIN: `${API_BASE_URL}/api/users/login`,
  REGISTER: `${API_BASE_URL}/api/users/register`,
  LOGOUT: `${API_BASE_URL}/api/users/logout`,
  // Backend expects DELETE on /api/users/account
  DELETE_ACCOUNT: `${API_BASE_URL}/api/users/delete`,
  UPLOAD_IMAGES: `${API_BASE_URL}/api/images/upload`,
  SEARCH_IMAGES: `${API_BASE_URL}/api/images/search`,
  GET_FOLDERS: `${API_BASE_URL}/api/folders`,
  DELETE_FOLDERS: `${API_BASE_URL}/api/folders`,
  FOLDERS_SHARED_WITH_ME: `${API_BASE_URL}/api/folders/shared`,
  SHARE_FOLDER: `${API_BASE_URL}/api/folders/share`,
  // Deprecated endpoints (not implemented in Java backend yet)
  PUBLIC_FOLDERS: '/public-folders',
  TOGGLE_PUBLIC: '/toggle-public',
  GENERATE_SHARE_LINK: '/generate-share-link',
  SHARE_WITH_USER: '/share-with-user',
};

// HTTP Status Codes
export const HTTP_STATUS = {
  OK: 200,
  CREATED: 201,
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  NOT_FOUND: 404,
  UNPROCESSABLE_ENTITY: 422,
  INTERNAL_SERVER_ERROR: 500,
};

/**
 * Custom error class for API errors with structured information
 */
export class APIError extends Error {
  constructor(message, status, details = null) {
    super(message);
    this.name = 'APIError';
    this.status = status;
    this.details = details;
  }
}

/**
 * Base API request handler with error handling
 * @private
 */
async function apiRequest(url, options = {}) {
  try {
    const response = await fetch(url, options);

    // Try to parse JSON, but fall back to plain text when not JSON
    let data = null;
    try {
      data = await response.json();
    } catch (jsonErr) {
      // not JSON - read raw text
      try {
        const text = await response.text();
        data = text;
      } catch (txtErr) {
        data = null;
      }
    }

    if (!response.ok) {
      // Prefer structured message when available
      const message = (data && typeof data === 'object' && (data.detail || data.message))
        || (typeof data === 'string' && data)
        || 'Request failed';
      throw new APIError(message, response.status, data);
    }

    return data;
  } catch (error) {
    if (error instanceof APIError) {
      throw error;
    }
    // Network errors or JSON parse errors
    throw new APIError(error.message || 'Network error', 0, { originalError: error });
  }
}

/**
 * Helper for requests that send FormData (uploads). Does not set Content-Type so browser will set the boundary.
 * @private
 */
async function formRequest(endpoint, formData, method = 'POST') {
  try {
    const response = await fetch(endpoint, { method, body: formData });
    // try to parse json, fall back to text
    let data;
    try {
      data = await response.json();
    } catch (e) {
      const text = await response.text();
      throw new APIError(text || 'Request failed', response.status);
    }

    if (!response.ok) {
      throw new APIError(data.detail || data.message || 'Request failed', response.status, data);
    }

    return data;
  } catch (error) {
    if (error instanceof APIError) throw error;
    throw new APIError(error.message || 'Network error', 0, { originalError: error });
  }
}

// ============== Additional API helpers ==============

/**
 * Upload images using FormData
 * @param {FormData} formData
 */
export async function uploadImagesForm(formData) {
  return formRequest(API_ENDPOINTS.UPLOAD_IMAGES, formData, 'POST');
}

/**
 * Fetch folders for the current user
 */
export async function getFolders() {
  const token = getToken();
  const params = new URLSearchParams({ token });
  return apiRequest(`${API_ENDPOINTS.GET_FOLDERS}?${params.toString()}`, { method: 'GET' });
}

/**
 * Search images
 */
export async function searchImages(query, folderIds = [], top_k = 5) {
  const token = getToken();
  const params = new URLSearchParams({ token, query, top_k: String(top_k) });
  if (folderIds && folderIds.length > 0) params.append('folder_ids', folderIds.join(','));
  return apiRequest(`${API_ENDPOINTS.SEARCH_IMAGES}?${params.toString()}`, { method: 'GET' });
}

/**
 * Delete folders (array of ids)
 */
export async function deleteFolders(folderIds) {
  const token = getToken();
  return apiRequest(API_ENDPOINTS.DELETE_FOLDERS, {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token, folder_ids: folderIds }),
  });
}

/** Fetch folders shared with the current user */
export async function getFoldersSharedWithMe() {
  const token = getToken();
  const params = new URLSearchParams({ token });
  return apiRequest(`${API_ENDPOINTS.FOLDERS_SHARED_WITH_ME}?${params.toString()}`, { method: 'GET' });
}

/**
 * Share a folder by id with username
 * @param {object} payload - { token, folderId, targetUsername, permission }
 */
export async function shareFolder(payload) {
  return post(API_ENDPOINTS.SHARE_FOLDER, payload);
}

/** Get public folders */
export async function getPublicFolders() {
  return apiRequest(API_ENDPOINTS.PUBLIC_FOLDERS, { method: 'GET' });
}

/** Toggle public status / update description */
export async function togglePublic(payload) {
  return post(API_ENDPOINTS.TOGGLE_PUBLIC, payload);
}

/** Generate share link for folder */
export async function generateShareLink(payload) {
  return post(API_ENDPOINTS.GENERATE_SHARE_LINK, payload);
}

/** Share with user (specific API expecting token, folder_id, target_username, permission) */
export async function shareWithUser(payload) {
  return post(API_ENDPOINTS.SHARE_WITH_USER, payload);
}


/**
 * POST request helper
 * @private
 */
async function post(endpoint, body) {
  return apiRequest(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

// ============== User Authentication API ==============

/**
 * Login user and create session
 * 
 * @param {string} username - User's username
 * @param {string} password - User's password
 * @returns {Promise<{token: string, user_id: number, username: string, message: string}>}
 * @throws {APIError} If credentials are invalid or server error
 * 
 * @example
 * try {
 *   const user = await loginUser('john', 'secret123');
 *   console.log(`Logged in as ${user.username}, token: ${user.token}`);
 * } catch (error) {
 *   if (error.status === 401) {
 *     console.error('Invalid credentials');
 *   }
 * }
 */
export async function loginUser(username, password) {
  return post(API_ENDPOINTS.LOGIN, { username, password });
}

/**
 * Register new user account
 * 
 * @param {string} username - Desired username
 * @param {string} password - Account password (min 6 characters)
 * @returns {Promise<{id: number, username: string}>}
 * @throws {APIError} If username exists or validation fails
 */
export async function registerUser(username, password) {
  return post(API_ENDPOINTS.REGISTER, { username, password });
}

/**
 * Logout user and invalidate session
 * 
 * @param {string} token - Session token
 * @returns {Promise<{message: string}>}
 * @throws {APIError} If token is invalid
 */
export async function logoutUser(token) {
  return post(API_ENDPOINTS.LOGOUT, { token });
}

/**
 * Delete the currently authenticated user's account
 * @param {string} token - Session token
 * @returns {Promise<{message: string}>}
 * @throws {APIError}
 */
export async function deleteAccount(token) {
  return apiRequest(API_ENDPOINTS.DELETE_ACCOUNT, {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token }),
  });
}

// ============== Token Management ==============

/**
 * Get stored authentication token
 * @returns {string|null} Token or null if not logged in
 */
export function getToken() {
  return localStorage.getItem('token');
}

/**
 * Save authentication token
 * @param {string} token - Session token to store
 */
export function saveToken(token) {
  localStorage.setItem('token', token);
}

/**
 * Remove authentication token (logout)
 */
export function clearToken() {
  localStorage.removeItem('token');
}

/**
 * Check if user is authenticated
 * @returns {boolean} True if token exists
 */
export function isAuthenticated() {
  return !!getToken();
}
