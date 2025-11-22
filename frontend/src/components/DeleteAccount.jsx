import React from 'react';
import { deleteAccount, clearToken } from '../utils/api';

function DeleteAccount({ onLogout }) {
  const handleDelete = async () => {
    const confirmed = window.confirm(
      'Delete account? This will remove your account and all associated data. This action cannot be undone.'
    );
    if (!confirmed) return;
    try {
      const token = localStorage.getItem('token');
      await deleteAccount(token);
      // Clear token and call parent logout handler
      clearToken();
      if (onLogout) onLogout();
      alert('Your account has been deleted.');
    } catch (err) {
      console.error('Delete account failed', err);
      // Prefer a human-readable message; fallback to stringified details
      const msg = (err && err.message)
        ? (typeof err.message === 'object' ? JSON.stringify(err.message) : err.message)
        : (err && err.details ? JSON.stringify(err.details) : JSON.stringify(err));
      alert(msg || 'Failed to delete account.');
    }
  };

  return (
    <button
      onClick={handleDelete}
      style={{
        padding: '8px 18px',
        background: 'linear-gradient(135deg, #ff416c 0%, #ff4b2b 100%)',
        color: 'white',
        border: 'none',
        borderRadius: '8px',
        fontSize: '13px',
        fontWeight: '600',
        cursor: 'pointer',
        transition: 'all 0.3s ease',
        boxShadow: '0 2px 8px rgba(255, 77, 77, 0.25)'
      }}
      onMouseOver={(e) => {
        e.target.style.transform = 'translateY(-2px)';
        e.target.style.boxShadow = '0 4px 12px rgba(255, 75, 75, 0.45)';
      }}
      onMouseOut={(e) => {
        e.target.style.transform = 'translateY(0)';
        e.target.style.boxShadow = '0 2px 8px rgba(255, 75, 75, 0.25)';
      }}
    >
      Delete Account
    </button>
  );
}

export default DeleteAccount;
