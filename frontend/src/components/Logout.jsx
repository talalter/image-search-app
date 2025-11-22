import React from 'react';
import { clearToken, logoutUser } from '../utils/api';

function LogOut({ onLogout }) {
  const handleLogout = async () => {
    try {
      const token = localStorage.getItem('token');
      await logoutUser(token);
      // Clear token from localStorage
      clearToken();
      // Call parent callback to update app state
      if (onLogout) {
        onLogout();
      }
    } catch (error) {
      console.error('Error during logout:', error);
      alert('Error during logout. Please try again.');
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <button 
        onClick={handleLogout}
      style={{
        padding: '10px 20px',
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        color: 'white',
        border: 'none',
        borderRadius: '8px',
        fontSize: '14px',
        fontWeight: '600',
        cursor: 'pointer',
        transition: 'all 0.3s ease',
        boxShadow: '0 2px 8px rgba(112, 250, 0, 0.3)'
      }}
      onMouseOver={(e) => {
        e.target.style.transform = 'translateY(-2px)';
        e.target.style.boxShadow = '0 4px 12px rgba(102, 126, 234, 0.5)';
      }}
      onMouseOut={(e) => {
        e.target.style.transform = 'translateY(0)';
        e.target.style.boxShadow = '0 2px 8px rgba(102, 126, 234, 0.3)';
      }}
    >
      Logout
    </button>
    </div>
  );
}

export default LogOut;