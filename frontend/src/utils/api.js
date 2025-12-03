/**
 * API Functions Layer
 *
 * High-level API functions for all frontend components to use.
 * Imports configuration and base utilities from apiConfig.js
 *
 * Usage in components:
 * import { loginUser, searchImages, uploadImagesForm, APIError, HTTP_STATUS } from '../utils/api';
 */

import {
  API_ENDPOINTS,
  HTTP_STATUS,
  APIError,
  apiRequest,
  formRequest,
  post,
  getToken,
  saveToken,
  clearToken,
  isAuthenticated
} from './apiConfig';

// Re-export commonly used items for convenience
export { APIError, HTTP_STATUS, getToken, saveToken, clearToken, isAuthenticated };

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


// ============== Image & Folder API Functions ==============

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

/**
 * Get embedding job status
 * @param {number} jobId - Job ID
 * @param {string} token - Session token
 * @returns {Promise<{job_id: number, status: string, total_images: number, processed_images: number, progress_percentage: number}>}
 * @throws {APIError}
 */
export async function getJobStatus(jobId, token) {
  return apiRequest(`/api/images/job-status/${jobId}`, {
    method: 'GET',
    headers: { 'token': token }
  });
}

// ============== User Authentication API ==============
