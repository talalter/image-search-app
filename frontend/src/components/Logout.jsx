import React from 'react';

function LogOut({ onLogout }) {
  const handleLogout = async () => {
    try {
      const token = localStorage.getItem('token');
      const response = await fetch('/users/logout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ token }),
      });

      if (response.ok) {
        // Clear token from localStorage
        localStorage.removeItem('token');
        // Call parent callback to update app state
        if (onLogout) {
          onLogout();
        }
      } else {
        console.error('Logout failed');
        alert('Logout failed. Please try again.');
      }
    } catch (error) {
      console.error('Error during logout:', error);
      alert('Error during logout. Please try again.');
    }
  };

  return (
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
        boxShadow: '0 2px 8px rgba(102, 126, 234, 0.3)'
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
      🚪 Logout
    </button>
  );
}

export default LogOut;