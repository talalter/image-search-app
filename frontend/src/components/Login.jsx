import React, { useState, useCallback } from 'react';
import { loginUser, saveToken, APIError, HTTP_STATUS } from '../utils/api';
import UniMageLogo from './UniMageLogo';

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
          <label style={{ 
            display: 'block', 
            marginBottom: '8px', 
            color: '#475569',
            fontSize: '14px',
            fontWeight: '500'
          }}>
            Username
          </label>
          <input
            type="text"
            value={username}
            onChange={e => setUsername(e.target.value)}
            placeholder="Enter your username"
            required
            style={{
              width: '100%',
              padding: '12px 16px',
              border: '2px solid #e1e8ed',
              borderRadius: '10px',
              fontSize: '16px',
              transition: 'border-color 0.2s',
              boxSizing: 'border-box'
            }}
            onFocus={(e) => e.target.style.borderColor = '#667eea'}
            onBlur={(e) => e.target.style.borderColor = '#e1e8ed'}
          />
        </div>

        <div style={{ marginBottom: '24px' }}>
          <label style={{ 
            display: 'block', 
            marginBottom: '8px', 
            color: '#475569',
            fontSize: '14px',
            fontWeight: '500'
          }}>
            Password
          </label>
          <input
            type="password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            placeholder="Enter your password"
            required
            style={{
              width: '100%',
              padding: '12px 16px',
              border: '2px solid #e1e8ed',
              borderRadius: '10px',
              fontSize: '16px',
              transition: 'border-color 0.2s',
              boxSizing: 'border-box'
            }}
            onFocus={(e) => e.target.style.borderColor = '#667eea'}
            onBlur={(e) => e.target.style.borderColor = '#e1e8ed'}
          />
        </div>

        {error && (
          <div style={{
            padding: '12px 16px',
            backgroundColor: '#fee',
            color: '#c33',
            borderRadius: '8px',
            marginBottom: '20px',
            fontSize: '14px',
            borderLeft: '4px solid #e74c3c'
          }}>
            {error}
          </div>
        )}

        <button 
          type="submit" 
          disabled={loading}
          style={{
            width: '100%',
            padding: '14px',
            background: loading ? '#cbd5e1' : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            color: 'white',
            border: 'none',
            borderRadius: '10px',
            fontSize: '16px',
            fontWeight: '600',
            cursor: loading ? 'not-allowed' : 'pointer',
            transition: 'all 0.3s ease',
            boxShadow: loading ? 'none' : '0 4px 12px rgba(102, 126, 234, 0.4)'
          }}
          onMouseOver={(e) => {
            if (!loading) {
              e.target.style.transform = 'translateY(-2px)';
              e.target.style.boxShadow = '0 6px 16px rgba(102, 126, 234, 0.6)';
            }
          }}
          onMouseOut={(e) => {
            e.target.style.transform = 'translateY(0)';
            e.target.style.boxShadow = '0 4px 12px rgba(102, 126, 234, 0.4)';
          }}
        >
          {loading ? 'Signing in...' : 'Sign In'}
        </button>
      </form>
    </div>
  );
}

export default Login;
