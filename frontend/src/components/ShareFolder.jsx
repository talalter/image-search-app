import React, { useState, useEffect } from 'react';
import { getFolders as apiGetFolders, shareFolder } from '../utils/api';
import Modal from './Modal';
import sharedStyles from '../styles/shared.module.css';

function ShareFolder({ onClose, inline = false }) {
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
        const data = await apiGetFolders();
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

    const folderId = parseInt(selectedFolder);
    if (isNaN(folderId) || folderId <= 0) {
      setError('Invalid folder selection');
      return;
    }

    setLoading(true);

    try {
      const token = localStorage.getItem('token');
      
      if (!token) {
        setError('You are not logged in. Please login first.');
        setLoading(false);
        return;
      }

      const payload = { token, folderId, targetUsername: username.trim(), permission };
      
      // Debug log to see what's being sent
      console.log('ShareFolder payload:', payload);
      
      await shareFolder(payload);

      setMessage(`✅ Folder shared successfully with ${username}!`);
      setUsername('');
      setSelectedFolder('');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  // Render the main content
  const content = (
    <>
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
              <label className={sharedStyles.formLabel}>
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
              <label className={sharedStyles.formLabel}>
                Username to Share With
              </label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="Enter username"
                required
                className={sharedStyles.formInput}
              />
            </div>

            <div style={{ marginBottom: '24px' }}>
              <label className={sharedStyles.formLabel}>
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
              <div className={sharedStyles.successMessage}>
                {message}
              </div>
            )}

            {error && (
              <div className={sharedStyles.errorMessage}>
                {error}
              </div>
            )}

            <div style={{ display: 'flex', gap: '12px' }}>
              <button
                type="submit"
                disabled={loading}
                className={sharedStyles.primaryButton}
                style={{ flex: 1 }}
              >
                {loading ? 'Sharing...' : 'Share Folder'}
              </button>
              <button
                type="button"
                onClick={onClose}
                className={sharedStyles.secondaryButton}
                style={{ flex: 1 }}
              >
                Cancel
              </button>
            </div>
          </form>
        )}
    </>
  );

  // Inline mode - render without modal wrapper
  if (inline) {
    return <div style={{ padding: '0' }}>{content}</div>;
  }

  // Modal mode - use shared Modal component
  return (
    <Modal isOpen={true} onClose={onClose} title="📤 Share Folder">
      {content}
    </Modal>
  );
}

export default ShareFolder;
