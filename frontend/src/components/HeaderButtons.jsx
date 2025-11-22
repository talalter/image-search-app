import React, { useState } from 'react';
import LogOut from './Logout.jsx';
import DeleteAccount from './DeleteAccount';

function HeaderButtons({ 
  username, 
  onManage, 
  onLogout 
}) {
  const [showLogout, setShowLogout] = useState(false);
  return (
    <>
      <button
        className="user-badge"
        onClick={() => setShowLogout(prev => !prev)}
        aria-expanded={showLogout}
        type="button"
      >
        <span className="user-avatar">{username && username[0] ? username[0].toUpperCase() : '?'}</span>
        <span className="user-name">{username}</span>
      </button>
      {showLogout && (
        <div className="logout-popup">
          <LogOut onLogout={onLogout} />
          <p> </p>
          <DeleteAccount onLogout={onLogout} />
        </div>
      )}
      <div className="header-buttons">
        <button className="manage-button"
         onClick={onManage}
>
          📁 Manage Folders
        </button>
        {/* Shared With Me moved inside Manage Folders modal */}
        {/* Logout moved to appear when clicking the username */}
      </div>
    </>
  );
}

export default HeaderButtons;