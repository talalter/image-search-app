import React, { useState } from 'react';
import './App.css';
import Login from './components/Login.jsx';
import Register from './components/Register.jsx';
import SearchImage from './components/SearchImages.jsx';
import GetFolders from './components/GetFolders.jsx';
import UploadPanel from './components/UploadFoldersPanel.jsx';
import Modal from './components/Modal.jsx';
import Card from './components/Card.jsx';
import LogOut from './components/Logout.jsx';


function App() {
  const [user, setUser] = useState(null);
  const [showRegister, setShowRegister] = useState(false); 
  const [showManageModal, setShowManageModal] = useState(false);

  const [selectedFolderIdsforSearch, setSelectedFolderIdsforSearch] = useState([]);
  const [SelectedFolderIdsforUpload, setSelectedFolderIdsforUpload] = useState([]);
  if (!user) {
    return (
      <div className="centered-container">
        <div className="login-box">
          {showRegister ? (
            <>
              <Register />
              <p style={{ textAlign: 'center', marginTop: '24px', color: '#64748b' }}>
                Already have an account?{" "}
                <button 
                  onClick={() => setShowRegister(false)}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: '#667eea',
                    fontWeight: '600',
                    cursor: 'pointer',
                    textDecoration: 'underline',
                    fontSize: '14px'
                  }}
                  onMouseOver={(e) => e.target.style.color = '#764ba2'}
                  onMouseOut={(e) => e.target.style.color = '#667eea'}
                >
                  Log In
                </button>
              </p>
            </>
          ) : (
            <>
              <Login onLogin={setUser} />
              <p style={{ textAlign: 'center', marginTop: '24px', color: '#64748b' }}>
                Don't have an account?{" "}
                <button 
                  onClick={() => setShowRegister(true)}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: '#667eea',
                    fontWeight: '600',
                    cursor: 'pointer',
                    textDecoration: 'underline',
                    fontSize: '14px'
                  }}
                  onMouseOver={(e) => e.target.style.color = '#764ba2'}
                  onMouseOut={(e) => e.target.style.color = '#667eea'}
                >
                  Register
                </button>
              </p>
            </>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="app-container">
      {/* Header with Manage Button and Logout */}
      <div className="dashboard-header">
        <h2 className="welcome-text">Welcome, {user.username} 👋</h2>
        <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
          <button className="manage-button" onClick={() => setShowManageModal(true)}>
            📁 Manage Folders
          </button>
          <LogOut onLogout={() => setUser(null)} />
        </div>
      </div>

      {/* Main Content - Search Card */}
      <div className="main-content">
        <Card title="🔍 Search Images">
          <SearchImage selectedFolderIds={selectedFolderIdsforSearch}/>
          <div style={{ marginTop: '24px', paddingTop: '24px', borderTop: '1px solid #e1e8ed' }}>
            <GetFolders 
              selectedFolderIds={selectedFolderIdsforSearch}
              setSelectedFolderIds={setSelectedFolderIdsforSearch}
            />
          </div>
        </Card>
      </div>

      {/* Manage Folders Modal */}
      <Modal 
        isOpen={showManageModal} 
        onClose={() => setShowManageModal(false)}
        title="Manage Folders"
      >
        <UploadPanel
          selectedFolderIds={SelectedFolderIdsforUpload}
          setSelectedFolderIds={setSelectedFolderIdsforUpload}
        />
      </Modal>
    </div>
  );
}

export default App;

