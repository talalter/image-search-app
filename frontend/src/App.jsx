import React, { useState } from 'react';
import './App.css';
import Login from './components/Login.jsx';
import Register from './components/Register.jsx';
import SearchImage from './components/SearchImages.jsx';
import GetFolders from './components/GetFolders.jsx';
import UploadPanel from './components/UploadFoldersPanel.jsx';


function App() {
  const [user, setUser] = useState(null);
  const [showRegister, setShowRegister] = useState(false); 

  const [selectedFolderIdsforSearch, setSelectedFolderIdsforSearch] = useState([]);
  const [SelectedFolderIdsforUpload, setSelectedFolderIdsforUpload] = useState([]);
  if (!user) {
    return (
      <div className="centered-container">
        <div className="login-box">
          {showRegister ? (
            <>
              <Register />
              <p>
                Already have an account?{" "}
                <button onClick={() => setShowRegister(false)}>Log In</button>
              </p>
            </>
          ) : (
            <>
              <Login onLogin={setUser} />
              <p>
                Don’t have an account?{" "}
                <button onClick={() => setShowRegister(true)}>Register</button>
              </p>
            </>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="app-container">
      <h2 className="welcome-text">Welcome, {user.username}</h2>
      <div className="main-content">
        <div className="side-box upload">
          <UploadPanel
          selectedFolderIds={SelectedFolderIdsforUpload}
          setSelectedFolderIds={setSelectedFolderIdsforUpload}
          />
        </div>
        <div className="side-box search">
          <SearchImage selectedFolderIds={selectedFolderIdsforSearch}/>
          <GetFolders 
          selectedFolderIds={selectedFolderIdsforSearch}
          setSelectedFolderIds={setSelectedFolderIdsforSearch}/>
        </div>
      </div>
    </div>
  );
}

export default App;

