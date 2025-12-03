import React from 'react';
import { clearToken, logoutUser } from '../utils/api';
import styles from '../styles/components/Logout.module.css';

function Logout({ onLogout }) {
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
    <div className={styles.logoutContainer}>
      <button 
        onClick={handleLogout}
        className={styles.logoutButton}
      >
        Logout
      </button>
    </div>
  );
}

export default Logout;