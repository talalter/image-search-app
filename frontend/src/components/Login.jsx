import React, { useState } from 'react';
import { loginUser, saveToken, APIError, HTTP_STATUS } from '../utils/api';
import UniMageLogo from './UniMageLogo';
import sharedStyles from '../styles/shared.module.css';

/**
 * Login Component
 * 
 * Handles user authentication with:
 * - Client-side validation
 * - Error handling with specific messages
 * - Loading states
 * - Token management
 */
function Login({ onLogin }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  /**
   * Validate form inputs before submission
   * @returns {{valid: boolean, error: string|null}}
   */
  const validateForm = () => {
    if (!username.trim()) {
      return { valid: false, error: 'Username is required' };
    }
    if (username.length < 3) {
      return { valid: false, error: 'Username must be at least 3 characters' };
    }
    if (!password) {
      return { valid: false, error: 'Password is required' };
    }
    if (password.length < 6) {
      return { valid: false, error: 'Password must be at least 6 characters' };
    }
    return { valid: true, error: null };
  };

  /**
   * Handle login form submission
   */
  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    
    // Client-side validation
    const validation = validateForm();
    if (!validation.valid) {
      setError(validation.error);
      return;
    }
    
    setLoading(true);
    
    try {
      // Call API utility function
      const userData = await loginUser(username, password);
      
      // Save token to localStorage
      saveToken(userData.token);
      
      // Update parent component state
      onLogin(userData);
      
    } catch (err) {
      // Handle specific error cases
      if (err instanceof APIError) {
        if (err.status === HTTP_STATUS.UNAUTHORIZED) {
          setError('Invalid username or password');
        } else if (err.status === HTTP_STATUS.UNPROCESSABLE_ENTITY) {
          setError('Please check your input and try again');
        } else if (err.status >= HTTP_STATUS.INTERNAL_SERVER_ERROR) {
          setError('Server error. Please try again later');
        } else {
          setError(err.message);
        }
      } else {
        setError('Network error. Please check your connection');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <UniMageLogo size={80} />
      
      <h2 style={{ 
        textAlign: 'center', 
        marginBottom: '8px',
        color: '#07063aff',
        fontSize: '28px'
      }}>
        Welcome
      </h2>
      <p style={{ 
        textAlign: 'center', 
        color: '#3c4d64ff', 
        marginBottom: '32px',
        fontSize: '14px'
      }}>
        Sign in to continue to your image library
      </p>

      <form onSubmit={handleSubmit} autoComplete="off">
        <div style={{ marginBottom: '20px' }}>
          <label className={sharedStyles.formLabel}>
            Username
          </label>
          <input
            type="text"
            value={username}
            onChange={e => setUsername(e.target.value)}
            placeholder="Enter your username"
            required
            className={sharedStyles.formInput}
          />
        </div>

        <div style={{ marginBottom: '24px' }}>
          <label className={sharedStyles.formLabel}>
            Password
          </label>
          <input
            type="password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            placeholder="Enter your password"
            required
            className={sharedStyles.formInput}
          />
        </div>

        {error && (
          <div className={sharedStyles.errorMessage}>
            {error}
          </div>
        )}

        <button 
          type="submit" 
          disabled={loading}
          className={sharedStyles.primaryButton}
          style={{ width: '100%' }}
        >
          {loading ? 'Signing in...' : 'Sign In'}
        </button>
      </form>
    </div>
  );
}

export default Login;
