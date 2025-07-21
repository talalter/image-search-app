import React, { useState } from 'react';
import './App.css';
import Login from './components/Login.jsx';
import SearchImage from './components/SearchImages.jsx';
import GetFolders from './components/GetFolders.jsx';
import UploadPanel from './components/UploadFoldersPanel.jsx';



function App() {
  const [user, setUser] = useState(null);
  const [selectedFolderIdsforSearch, setSelectedFolderIdsforSearch] = useState([]);
  const [SelectedFolderIdsforUpload, setSelectedFolderIdsforUpload] = useState([]);
  if (!user) {
    return (
      <div className="centered-container">
        <div className="login-box">
          <Login onLogin={setUser} />
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


// import React, { useState } from 'react';

// function FolderImageUploader() {
//   const [folderName, setFolderName] = useState('');
//   const [files, setFiles] = useState([]);

//   const handleFolderSelect = (e) => {
//     const files = e.target.files;
//     if (files.length === 0) return;

//     const firstPath = files[0].webkitRelativePath;
//     const folder = firstPath.split('/')[0];
//     setFolderName(folder);
//     const imageData = Array.from(files)
//     setFiles(imageData);
//   };

//   return (
//     <div style={{ padding: '20px' }}>
//       <h2>Select a folder with images</h2>
//       <input
//         type="file"
//         webkitdirectory="true"
//         directory=""
//         multiple
//         onChange={handleFolderSelect}
//       />

//       {folderName && <p><strong>Folder:</strong> {folderName}</p>}

//       {files.length > 0 && (
//         <ul>
//           {Array.from(files).map((img, idx) => (
//             <li key={idx}>{img.relativePath}</li>
//           ))}
//         </ul>
//       )}
//     </div>
//   );
// }

// export default FolderImageUploader;
