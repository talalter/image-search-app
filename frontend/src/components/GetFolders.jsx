import React, { useState, useEffect } from 'react';


async function getFolders() {
  const token = localStorage.getItem('token');
  const params = new URLSearchParams({ token });

  const res = await fetch(`/get-folders?${params.toString()}`, {
    method: 'GET',
  });

  if (!res.ok) {
    const errorText = await res.text();
    throw new Error('Failed to fetch folders: ' + errorText);
  }

  return await res.json();
}

function GetFolders({selectedFolderIds, setSelectedFolderIds}) {
  const [folders, setFolders] = useState([]);
  const [error, setError] = useState('');
  const [showFolders, setShowFolders] = useState(false);

  const fetchFolders = async () => {
    if (showFolders) {
      setShowFolders(false);
      return;
    }
    setError('');
    try {
      const res = await getFolders();
      setFolders(res.folders);
      setShowFolders(true);
    } catch (err) {
      setError(err.message);
      setFolders([]);
    }
  };

  // Auto-fetch folders when component mounts
  useEffect(() => {
    const autoFetch = async () => {
      setError('');
      try {
        const res = await getFolders();
        setFolders(res.folders);
        setShowFolders(true);
      } catch (err) {
        setError(err.message);
        setFolders([]);
      }
    };
    autoFetch();
  }, []); // Empty dependency array = run once on mount

  const handleClick = (folderId) => {
    setSelectedFolderIds(prev => {
      if (prev.includes(folderId)) {
        return prev.filter(id => id !== folderId);
      } else {
        return [...prev, folderId];
      }
    });
  };

  return (
    <div style={{ display: 'flex', justifyContent: 'flex-end', padding: '20px' }}>
      <div style={{ width: '300px' }}>
        <h2>Folder List</h2>
        <button onClick={fetchFolders}>
          {showFolders ? 'Hide Folders' : 'My Folders'}
        </button>

        {error && <p style={{ color: 'red' }}>{error}</p>}

        {showFolders && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginTop: '10px' }}>
            {folders.map(folder => (
              <div
                key={folder.id}
                onClick={() => handleClick(folder.id)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  border: '2px solid #ccc',
                  borderRadius: '8px',
                  padding: '10px',
                  backgroundColor: selectedFolderIds.includes(folder.id) ? '#d0f0c0' : '#f9f9f9',
                  cursor: 'pointer'
                }}
              >
                <span>{folder.folder_name}</span>
                <span>{selectedFolderIds.includes(folder.id) ? '✔️' : ''}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export default GetFolders;