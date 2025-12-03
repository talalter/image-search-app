import React from 'react';
import { deleteAccount, clearToken } from '../utils/api';
import styles from '../styles/components/DeleteAccount.module.css';

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
      className={styles.deleteButton}
    >
      Delete Account
    </button>
  );
}

export default DeleteAccount;
