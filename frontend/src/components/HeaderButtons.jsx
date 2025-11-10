import React from 'react';
import LogOut from './Logout.jsx';

function HeaderButtons({ 
  username, 
  onManage, 
  onShare, 
  onSharedWithMe, 
  onLogout 
}) {
  return (
    <>
      <h2 className="welcome-text">Welcome, {username}</h2>
      <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
        <button className="manage-button" onClick={onManage}>
          📁 Manage Folders
        </button>
        <button 
          className="manage-button" 
          onClick={onShare}
          style={{ background: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)' }}
        >
          📤 Share Folder
        </button>
        <button 
          className="manage-button" 
          onClick={onSharedWithMe}
          style={{ background: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)' }}
        >
          📥 Shared With Me
        </button>
        <LogOut onLogout={onLogout} />
      </div>
    </>
  );
}

export default HeaderButtons;