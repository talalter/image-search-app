import React, { useState } from 'react';
import { togglePublic, generateShareLink, shareWithUser } from '../utils/api';

function FolderSharing({ folder, onClose, onUpdate }) {
  const [isPublic, setIsPublic] = useState(folder.is_public || false);
  const [description, setDescription] = useState(folder.description || '');
  const [shareToken, setShareToken] = useState(folder.share_token || '');
  const [targetUsername, setTargetUsername] = useState('');
  const [permission, setPermission] = useState('view');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const handleTogglePublic = async () => {
    setLoading(true);
    setError('');
    setMessage('');

    try {
      const token = localStorage.getItem('token');
      const data = await togglePublic({ token, folder_id: folder.id, is_public: !isPublic, description });
      setIsPublic(!isPublic);
      setMessage(data.message);
      if (onUpdate) onUpdate();
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleGenerateShareLink = async () => {
    setLoading(true);
    setError('');
    setMessage('');

    try {
      const token = localStorage.getItem('token');
      const data = await generateShareLink({ token, folder_id: folder.id });
      setShareToken(data.share_token);
      setMessage('Share link generated successfully! Copy the link below.');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleShareWithUser = async () => {
    if (!targetUsername.trim()) {
      setError('Please enter a username');
      return;
    }

    setLoading(true);
    setError('');
    setMessage('');

    try {
      const token = localStorage.getItem('token');
      const data = await shareWithUser({ token, folder_id: folder.id, target_username: targetUsername, permission });
      setMessage(`Folder shared with ${targetUsername}!`);
      setTargetUsername('');
      if (onUpdate) onUpdate();
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const copyToClipboard = (text) => {
    navigator.clipboard.writeText(text);
    setMessage('Link copied to clipboard!');
  };

  const shareUrl = shareToken ? `${window.location.origin}/shared-folder/${shareToken}` : '';

  return (
    <div style={{
      position: 'fixed',
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
      backgroundColor: 'rgba(0, 0, 0, 0.5)',
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      zIndex: 1000
    }}>
      <div style={{
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        padding: '30px',
        borderRadius: '20px',
        maxWidth: '600px',
        width: '90%',
        maxHeight: '80vh',
        overflowY: 'auto',
        boxShadow: '0 10px 40px rgba(0,0,0,0.3)'
      }}>
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '20px'
        }}>
          <h2 style={{ margin: 0, color: 'white' }}>
            Share "{folder.folder_name}"
          </h2>
          <button
            onClick={onClose}
            style={{
              background: 'rgba(255,255,255,0.2)',
              border: 'none',
              borderRadius: '50%',
              width: '30px',
              height: '30px',
              color: 'white',
              cursor: 'pointer',
              fontSize: '18px'
            }}
          >
            ×
          </button>
        </div>

        {message && (
          <div style={{
            padding: '10px',
            backgroundColor: 'rgba(76, 175, 80, 0.2)',
            color: '#2e7d32',
            borderRadius: '8px',
            marginBottom: '15px',
            backdropFilter: 'blur(10px)'
          }}>
            {message}
          </div>
        )}

        {error && (
          <div style={{
            padding: '10px',
            backgroundColor: 'rgba(244, 67, 54, 0.2)',
            color: '#c62828',
            borderRadius: '8px',
            marginBottom: '15px',
            backdropFilter: 'blur(10px)'
          }}>
            {error}
          </div>
        )}

        {/* Description */}
        <div style={{ marginBottom: '25px' }}>
          <label style={{ display: 'block', color: 'white', marginBottom: '8px', fontWeight: 'bold' }}>
            Folder Description
          </label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Add a description for this folder..."
            style={{
              width: '100%',
              padding: '10px',
              borderRadius: '8px',
              border: '2px solid rgba(255,255,255,0.3)',
              backgroundColor: 'rgba(255,255,255,0.9)',
              fontSize: '14px',
              minHeight: '60px',
              resize: 'vertical'
            }}
          />
        </div>

        {/* Public Toggle */}
        <div style={{
          backgroundColor: 'rgba(255,255,255,0.15)',
          padding: '20px',
          borderRadius: '12px',
          marginBottom: '20px',
          backdropFilter: 'blur(10px)'
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <h3 style={{ margin: '0 0 5px 0', color: 'white' }}>📢 Public Folder</h3>
              <p style={{ margin: 0, color: 'rgba(255,255,255,0.8)', fontSize: '14px' }}>
                Make visible to all users
              </p>
            </div>
            <button
              onClick={handleTogglePublic}
              disabled={loading}
              style={{
                padding: '10px 20px',
                backgroundColor: isPublic ? '#f44336' : '#4CAF50',
                color: 'white',
                border: 'none',
                borderRadius: '8px',
                cursor: loading ? 'not-allowed' : 'pointer',
                fontWeight: 'bold',
                transition: 'all 0.3s'
              }}
            >
              {loading ? '...' : (isPublic ? 'Make Private' : 'Make Public')}
            </button>
          </div>
        </div>

        {/* Share Link */}
        <div style={{
          backgroundColor: 'rgba(255,255,255,0.15)',
          padding: '20px',
          borderRadius: '12px',
          marginBottom: '20px',
          backdropFilter: 'blur(10px)'
        }}>
          <h3 style={{ margin: '0 0 10px 0', color: 'white' }}>🔗 Share Link</h3>
          <p style={{ margin: '0 0 15px 0', color: 'rgba(255,255,255,0.8)', fontSize: '14px' }}>
            Anyone with the link can view this folder
          </p>
          
          {shareUrl ? (
            <div>
              <div style={{
                display: 'flex',
                gap: '10px',
                marginBottom: '10px'
              }}>
                <input
                  type="text"
                  value={shareUrl}
                  readOnly
                  style={{
                    flex: 1,
                    padding: '10px',
                    borderRadius: '8px',
                    border: '2px solid rgba(255,255,255,0.3)',
                    backgroundColor: 'rgba(255,255,255,0.9)',
                    fontSize: '14px'
                  }}
                />
                <button
                  onClick={() => copyToClipboard(shareUrl)}
                  style={{
                    padding: '10px 20px',
                    backgroundColor: '#2196F3',
                    color: 'white',
                    border: 'none',
                    borderRadius: '8px',
                    cursor: 'pointer',
                    fontWeight: 'bold'
                  }}
                >
                  Copy
                </button>
              </div>
            </div>
          ) : (
            <button
              onClick={handleGenerateShareLink}
              disabled={loading}
              style={{
                padding: '10px 20px',
                backgroundColor: '#2196F3',
                color: 'white',
                border: 'none',
                borderRadius: '8px',
                cursor: loading ? 'not-allowed' : 'pointer',
                fontWeight: 'bold',
                width: '100%'
              }}
            >
              {loading ? 'Generating...' : 'Generate Share Link'}
            </button>
          )}
        </div>

        {/* Share with User */}
        <div style={{
          backgroundColor: 'rgba(255,255,255,0.15)',
          padding: '20px',
          borderRadius: '12px',
          backdropFilter: 'blur(10px)'
        }}>
          <h3 style={{ margin: '0 0 10px 0', color: 'white' }}>👥 Share with User</h3>
          <p style={{ margin: '0 0 15px 0', color: 'rgba(255,255,255,0.8)', fontSize: '14px' }}>
            Share with a specific user by username
          </p>
          
          <div style={{ marginBottom: '10px' }}>
            <input
              type="text"
              value={targetUsername}
              onChange={(e) => setTargetUsername(e.target.value)}
              placeholder="Enter username"
              style={{
                width: '100%',
                padding: '10px',
                borderRadius: '8px',
                border: '2px solid rgba(255,255,255,0.3)',
                backgroundColor: 'rgba(255,255,255,0.9)',
                fontSize: '14px',
                marginBottom: '10px'
              }}
            />
          </div>

          <div style={{ marginBottom: '15px' }}>
            <label style={{ display: 'block', color: 'white', marginBottom: '8px', fontSize: '14px' }}>
              Permission:
            </label>
            <select
              value={permission}
              onChange={(e) => setPermission(e.target.value)}
              style={{
                width: '100%',
                padding: '10px',
                borderRadius: '8px',
                border: '2px solid rgba(255,255,255,0.3)',
                backgroundColor: 'rgba(255,255,255,0.9)',
                fontSize: '14px'
              }}
            >
              <option value="view">View Only</option>
              <option value="edit">Can Edit</option>
            </select>
          </div>

          <button
            onClick={handleShareWithUser}
            disabled={loading}
            style={{
              padding: '10px 20px',
              backgroundColor: '#9C27B0',
              color: 'white',
              border: 'none',
              borderRadius: '8px',
              cursor: loading ? 'not-allowed' : 'pointer',
              fontWeight: 'bold',
              width: '100%'
            }}
          >
            {loading ? 'Sharing...' : 'Share with User'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default FolderSharing;
