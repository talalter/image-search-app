import React, { useState, useCallback } from 'react';
import './App.css';
import Login from './components/Login.jsx';
import authStyles from './styles/AppAuth.module.css';
import Register from './components/Register.jsx';
import SearchImage from './components/SearchImages.jsx';
import GetFolders from './components/GetFolders.jsx';
import UploadFolderPanel from './components/UploadFoldersPanel.jsx';
import Modal from './components/Modal.jsx';
import Card from './components/Card.jsx';
import HeaderButtons from './components/HeaderButtons.jsx';
import ShareFolder from './components/ShareFolder.jsx';
import SharedWithMe from './components/SharedWithMe.jsx';


function App() {
  const [user, setUser] = useState(null);
  const [showRegister, setShowRegister] = useState(false);
   
  const [showManageModal, setShowManageModal] = useState(false);
  const [showShareModal, setShowShareModal] = useState(false);
  const [showSharedWithMeModal, setShowSharedWithMeModal] = useState(false);

  const [selectedFoldersForSearch, setSelectedFoldersForSearch] = useState([]);
  const [selectedFoldersForUpload, setSelectedFoldersForUpload] = useState([]);
  
  // Memoized event handlers for better performance
  const handleManage = useCallback(() => setShowManageModal(true), []);
  const handleShare = useCallback(() => setShowShareModal(true), []);
  const handleSharedWithMe = useCallback(() => setShowSharedWithMeModal(true), []);
  const handleLogout = useCallback(() => {
    setUser(null);
    setShowRegister(false);
  }, []);

  // Event handlers object with memoized functions
  const handlers = {
    onManage: handleManage,
    onShare: handleShare,
    onSharedWithMe: handleSharedWithMe,
    onLogout: handleLogout
  };

  if (!user) {
    return (
      <div className="centered-container">
        <div className="login-box">
          {showRegister ? (
            <>
              <Register onRegisterSuccess={setUser} />
              <p className={authStyles.authHint}>
                Already have an account?{" "}
                <button 
                  onClick={() => setShowRegister(false)}
                  className={authStyles.linkButton}
                >
                  Log In
                </button>
              </p>
            </>
          ) : (
            <>
              <Login onLogin={setUser} />
              <p className={authStyles.authHint}>
                Don't have an account?{" "}
                <button 
                  onClick={() => setShowRegister(true)}
                  className={authStyles.linkButton}
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
      {/* Header */}
      <div className="dashboard-header">
        <HeaderButtons
          username={user.username}
          {...handlers}
        />
      </div>

      {/* Main Content - Search Card */}
      <div className="main-content">
        <Card title="Search Images">
          <SearchImage selectedFolderIds={selectedFoldersForSearch}/>
          <div className={authStyles.divider}>
            <GetFolders 
              selectedFolderIds={selectedFoldersForSearch}
              setSelectedFolderIds={setSelectedFoldersForSearch}
              refreshTrigger={showManageModal}
            />
          </div>
        </Card>
      </div>

      {/* Manage Folders Modal */}
      {showManageModal && (
        <Modal 
          isOpen={showManageModal} 
          onClose={() => setShowManageModal(false)}
          title="Manage Folders">
          <UploadFolderPanel
            selectedFolderIds={selectedFoldersForUpload}
            setSelectedFolderIds={setSelectedFoldersForUpload}
          />
        </Modal>
      )}

      {/* Share Folder Modal */}
      {showShareModal && (
        <ShareFolder 
          onClose={() => setShowShareModal(false)}
        />
      )}

      {/* Shared With Me Modal */}
      {showSharedWithMeModal && (
        <SharedWithMe 
          onClose={() => setShowSharedWithMeModal(false)}
        />
      )}
    </div>
  );
}

export default App;

