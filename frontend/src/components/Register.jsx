import React, { useState } from 'react';
import UniMageLogo from './UniMageLogo';
import { loginUser, saveToken, registerUser } from '../utils/api';
import sharedStyles from '../styles/shared.module.css';

function Register({ onRegisterSuccess }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);
  
  const handleRegister = async (e) => {
    e.preventDefault();
    setMessage('');
    setError('');

    // Validate password length
    if (password.length < 6) {
      setError('Password must be at least 6 characters');
      return;
    }

    setLoading(true);

    try {
      await registerUser(username, password);

      // Account created! Now automatically log them in
      setMessage(`Account created! Logging you in...`);
      
      // Auto-login after successful registration
      try {
        const loginData = await loginUser(username, password);
        saveToken(loginData.token);
        
        // Call parent callback to update user state
        if (onRegisterSuccess) {
          onRegisterSuccess(loginData);
        }
      } catch (loginErr) {
        // Registration succeeded but auto-login failed
        setMessage(`Account created! Please login manually.`);
        setUsername('');
        setPassword('');
      }
    } catch (err) {
      setError(err.message);
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
        color: '#2c3e50',
        fontSize: '28px'
      }}>
        Create Account
      </h2>
      <p style={{ 
        textAlign: 'center', 
        color: '#64748b', 
        marginBottom: '32px',
        fontSize: '14px'
      }}>
        Join us to start searching images with AI
      </p>

      <form onSubmit={handleRegister} autoComplete="off">
        <div style={{ marginBottom: '20px' }}>
          <label className={sharedStyles.formLabel}>
            Username
          </label>
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="Choose a username"
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
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Create a password (min 6 characters)"
            required
            className={sharedStyles.formInput}
          />
        </div>

        {message && (
          <div className={sharedStyles.successMessage}>
            {message}
          </div>
        )}

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
          {loading ? 'Creating Account...' : 'Create Account'}
        </button>
      </form>
    </div>
  );
}

export default Register;
