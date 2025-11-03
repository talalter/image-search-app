import React, { useState, useEffect } from 'react';

function ShareFolder({ onClose }) {
  const [folders, setFolders] = useState([]);
  const [selectedFolder, setSelectedFolder] = useState('');
  const [username, setUsername] = useState('');
  const [permission, setPermission] = useState('view');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [loadingFolders, setLoadingFolders] = useState(true);

  // Fetch folders when component mounts
  useEffect(() => {
    const fetchFolders = async () => {
      try {
        const token = localStorage.getItem('token');
        const res = await fetch(`/get-folders?token=${token}`);
        const data = await res.json();
        
        if (!res.ok) {
          throw new Error(data.detail || 'Failed to fetch folders');
        }
        
        // Filter to only show owned folders (not shared ones)
        const ownedFolders = data.folders.filter(f => f.is_owner !== false);
        setFolders(ownedFolders);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoadingFolders(false);
      }
    };
    
    fetchFolders();
  }, []);

  const handleShare = async (e) => {
    e.preventDefault();
    setMessage('');
    setError('');

    if (!selectedFolder) {
      setError('Please select a folder');
      return;
    }

    if (!username.trim()) {
      setError('Please enter a username');
      return;
    }

    setLoading(true);

    try {
      const token = localStorage.getItem('token');
      const res = await fetch('/share-folder', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          token,
          folder_id: parseInt(selectedFolder),
          username: username.trim(),
          permission
        }),
      });

      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.detail || 'Sharing failed');
      }

      setMessage(`✅ Folder shared successfully with ${username}!`);
      setUsername('');
      setSelectedFolder('');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      position: 'fixed',
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
      background: 'rgba(0, 0, 0, 0.5)',
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      zIndex: 1000
    }}>
      <div style={{
        background: 'white',
        borderRadius: '16px',
        padding: '32px',
        maxWidth: '500px',
        width: '90%',
        maxHeight: '80vh',
        overflow: 'auto',
        boxShadow: '0 20px 60px rgba(0, 0, 0, 0.3)'
      }}>
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '24px'
        }}>
          <h2 style={{
            margin: 0,
            color: '#2c3e50',
            fontSize: '24px'
          }}>
            📤 Share Folder
          </h2>
          <button
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              fontSize: '24px',
              cursor: 'pointer',
              color: '#64748b',
              padding: '0 8px'
            }}
          >
            ×
          </button>
        </div>

        {loadingFolders && (
          <div style={{ textAlign: 'center', padding: '20px', color: '#64748b' }}>
            Loading your folders...
          </div>
        )}

        {!loadingFolders && folders.length === 0 && !error && (
          <div style={{ textAlign: 'center', padding: '20px', color: '#64748b' }}>
            <p>You don't have any folders yet.</p>
            <p style={{ fontSize: '14px' }}>Upload some images first to create folders!</p>
          </div>
        )}

        {!loadingFolders && folders.length > 0 && (
          <form onSubmit={handleShare}>
            <div style={{ marginBottom: '20px' }}>
              <label style={{
                display: 'block',
                marginBottom: '8px',
                color: '#475569',
                fontSize: '14px',
                fontWeight: '500'
              }}>
                Select Folder
              </label>
              <select
                value={selectedFolder}
                onChange={(e) => setSelectedFolder(e.target.value)}
                required
                style={{
                  width: '100%',
                  padding: '12px 16px',
                  border: '2px solid #e1e8ed',
                  borderRadius: '10px',
                  fontSize: '16px',
                  transition: 'border-color 0.2s',
                  boxSizing: 'border-box',
                  background: 'white'
                }}
              >
                <option value="">Choose a folder...</option>
                {folders.map(folder => (
                  <option key={folder.id} value={folder.id}>
                    {folder.folder_name}
                  </option>
                ))}
              </select>
            </div>

            <div style={{ marginBottom: '20px' }}>
              <label style={{
                display: 'block',
                marginBottom: '8px',
                color: '#475569',
                fontSize: '14px',
                fontWeight: '500'
              }}>
                Username to Share With
              </label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="Enter username"
                required
                style={{
                  width: '100%',
                  padding: '12px 16px',
                  border: '2px solid #e1e8ed',
                  borderRadius: '10px',
                  fontSize: '16px',
                  transition: 'border-color 0.2s',
                  boxSizing: 'border-box'
                }}
              />
            </div>

            <div style={{ marginBottom: '24px' }}>
              <label style={{
                display: 'block',
                marginBottom: '8px',
                color: '#475569',
                fontSize: '14px',
                fontWeight: '500'
              }}>
                Permission Level
              </label>
              <select
                value={permission}
                onChange={(e) => setPermission(e.target.value)}
                style={{
                  width: '100%',
                  padding: '12px 16px',
                  border: '2px solid #e1e8ed',
                  borderRadius: '10px',
                  fontSize: '16px',
                  transition: 'border-color 0.2s',
                  boxSizing: 'border-box',
                  background: 'white'
                }}
              >
                <option value="view">View Only</option>
                <option value="edit">View & Edit</option>
              </select>
            </div>

            {message && (
              <div style={{
                padding: '12px 16px',
                backgroundColor: '#d4edda',
                color: '#155724',
                borderRadius: '8px',
                marginBottom: '20px',
                fontSize: '14px',
                borderLeft: '4px solid #28a745'
              }}>
                {message}
              </div>
            )}

            {error && (
              <div style={{
                padding: '12px 16px',
                backgroundColor: '#fee',
                color: '#c33',
                borderRadius: '8px',
                marginBottom: '20px',
                fontSize: '14px',
                borderLeft: '4px solid #e74c3c'
              }}>
                {error}
              </div>
            )}

            <div style={{ display: 'flex', gap: '12px' }}>
              <button
                type="submit"
                disabled={loading}
                style={{
                  flex: 1,
                  padding: '14px',
                  background: loading ? '#cbd5e1' : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                  color: 'white',
                  border: 'none',
                  borderRadius: '10px',
                  fontSize: '16px',
                  fontWeight: '600',
                  cursor: loading ? 'not-allowed' : 'pointer',
                  transition: 'all 0.3s ease'
                }}
              >
                {loading ? 'Sharing...' : 'Share Folder'}
              </button>
              <button
                type="button"
                onClick={onClose}
                style={{
                  flex: 1,
                  padding: '14px',
                  background: '#e2e8f0',
                  color: '#475569',
                  border: 'none',
                  borderRadius: '10px',
                  fontSize: '16px',
                  fontWeight: '600',
                  cursor: 'pointer',
                  transition: 'all 0.3s ease'
                }}
              >
                Cancel
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}

export default ShareFolder;
