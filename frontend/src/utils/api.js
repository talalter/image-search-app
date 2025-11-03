/**
 * API Utility Layer
 * 
 * Centralized API communication with:
 * - Consistent error handling
 * - Type documentation via JSDoc
 * - Reusable request logic
 */

// API Endpoints - Single source of truth
export const API_ENDPOINTS = {
  LOGIN: '/login',
  REGISTER: '/register',
  LOGOUT: '/logout',
  SEARCH_IMAGES: '/search-images',
  UPLOAD_IMAGES: '/upload-images',
  GET_FOLDERS: '/get-folders',
  DELETE_FOLDERS: '/delete-folders',
  DELETE_ACCOUNT: '/delete-account',
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
    const data = await response.json();

    if (!response.ok) {
      throw new APIError(
        data.detail || 'Request failed',
        response.status,
        data
      );
    }

    return data;
  } catch (error) {
    if (error instanceof APIError) {
      throw error;
    }
    // Network errors or JSON parse errors
    throw new APIError(
      error.message || 'Network error',
      0,
      { originalError: error }
    );
  }
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

/**
 * GET request helper
 * @private
 */
async function get(endpoint, params = {}) {
  const queryString = new URLSearchParams(params).toString();
  const url = queryString ? `${endpoint}?${queryString}` : endpoint;
  return apiRequest(url, { method: 'GET' });
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
