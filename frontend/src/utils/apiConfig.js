/**
 * API Configuration and Base Utilities
 *
 * Contains:
 * - Backend configuration and environment switching
 * - API endpoints configuration
 * - HTTP status codes
 * - Base request handlers and error classes
 * - Token management utilities
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
export async function apiRequest(url, options = {}) {
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
      // Handle Java backend validation errors (status 422 with errors object)
      if (response.status === 422 && data && data.errors) {
        const fieldErrors = Object.entries(data.errors)
          .map(([field, message]) => `${field}: ${message}`)
          .join(', ');
        const message = `${data.detail || 'Validation failed'}: ${fieldErrors}`;
        throw new APIError(message, response.status, data);
      }

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
export async function formRequest(endpoint, formData, method = 'POST') {
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

/**
 * POST request helper
 */
export async function post(endpoint, body) {
  return apiRequest(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
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