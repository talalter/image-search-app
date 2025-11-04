import React, { useState } from 'react';    
import UploadImages from './UploadImages.jsx';
import GetFolders from './GetFolders.jsx';


function UploadFolderPanel({ 
    selectedFolderIds, 
    setSelectedFolderIds 
  }) {
    const [mode, setMode] = useState(null); // upload_new | expand_existing | delete_folder
    const [refreshKey, setRefreshKey] = useState(0); // Force refresh of GetFolders

    const deleteFolders = async (folderIds) => {
      const token = localStorage.getItem("token");
      try {
        const res = await fetch('/delete-folders', {
          method: 'DELETE',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ token, folder_ids: folderIds }),
        });
        if (!res.ok) throw new Error(await res.text());
        alert("Folders deleted successfully");
        
        // Clear selection and force refresh
        setSelectedFolderIds([]);
        setRefreshKey(prev => prev + 1);
      } catch (err) {
        alert("Error deleting folders: " + err.message);
      }
    }
  
    return (
      <div className="upload-panel">
  
        <div className="mode-buttons">

        <button onClick={() => setMode(prev => prev === "upload_new" ? null : "upload_new")}>
        Upload New Folder
        </button>

        <button onClick={() => setMode(prev => prev === "expand_existing" ? null : "expand_existing")}>
        Expand Existing Folder
        </button>

        <button onClick={() => setMode(prev => prev === "delete_folder" ? null : "delete_folder")}>
        Delete Folder
        </button>

        </div>
  
        <div className="panel-content">
          {mode === "upload_new" && (
            <UploadImages 
              onUploadSuccess={() => setRefreshKey(prev => prev + 1)}
            />
          )}
  
          {(mode === "expand_existing" || mode === "delete_folder") && (
            <GetFolders
              key={refreshKey}
              selectedFolderIds={selectedFolderIds}
              setSelectedFolderIds={setSelectedFolderIds}
              mode={mode}
              refreshTrigger={refreshKey}
            />
          )}
  
          {mode === "expand_existing" && selectedFolderIds.length > 0 && (
            <UploadImages 
              folderId={selectedFolderIds[0]} 
              onUploadSuccess={() => setRefreshKey(prev => prev + 1)}
            />
          )}
  
          {mode === "delete_folder" && selectedFolderIds.length > 0 && (
            <button
              style={{ backgroundColor: 'red', color: 'white', marginTop: '10px' }}
              onClick={() => deleteFolders(selectedFolderIds)}
            >
              Delete Selected Folder(s)
            </button>
          )}
        </div>
      </div>
    );
  }

export default UploadFolderPanel;