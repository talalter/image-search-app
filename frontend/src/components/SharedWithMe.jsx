import React, { useState, useEffect } from 'react';
import { getFoldersSharedWithMe } from '../utils/api';
import Modal from './Modal';
import sharedStyles from '../styles/shared.module.css';

function SharedWithMe({ onClose, inline = false }) {
  const [sharedFolders, setSharedFolders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchSharedFolders();
  }, []);

  const fetchSharedFolders = async () => {
    try {
      const data = await getFoldersSharedWithMe();
      setSharedFolders(data.folders || []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const content = (
    <>
      {loading && (
        <div style={{ textAlign: 'center', padding: '12px', color: '#64748b' }}>Loading...</div>
      )}

      {error && (
        <div className={sharedStyles.errorMessage}>{error}</div>
      )}

      {!loading && !error && sharedFolders.length === 0 && (
        <div style={{ textAlign: 'center', padding: '24px 12px', color: '#64748b' }}>
          <div style={{ fontSize: '40px', marginBottom: '12px' }}>📂</div>
          <p>No folders have been shared with you yet.</p>
        </div>
      )}

      {!loading && !error && sharedFolders.length > 0 && (
        <div>
          {sharedFolders.map(folder => (
            <div key={folder.id} style={{ padding: '12px', border: '2px solid #e1e8ed', borderRadius: '10px', marginBottom: '10px', background: '#f8fafc' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                <h3 style={{ margin: 0, fontSize: '16px', color: '#2c3e50' }}>📁 {folder.folder_name}</h3>
                <span style={{ padding: '4px 10px', background: folder.permission === 'edit' ? '#dbeafe' : '#f0fdf4', color: folder.permission === 'edit' ? '#1e40af' : '#15803d', borderRadius: '12px', fontSize: '12px', fontWeight: '600' }}>{folder.permission === 'edit' ? '✏️ Edit' : '👁️ View'}</span>
              </div>
              <div style={{ fontSize: '14px', color: '#64748b' }}>Shared by: <strong>{folder.owner_username}</strong></div>
              <div style={{ fontSize: '12px', color: '#94a3b8', marginTop: '4px' }}>{new Date(folder.shared_at).toLocaleDateString()}</div>
            </div>
          ))}
        </div>
      )}
    </>
  );

  // Inline mode - render without modal wrapper
  if (inline) {
    return <div style={{ padding: '0' }}>{content}</div>;
  }

  // Modal mode - use shared Modal component
  return (
    <Modal isOpen={true} onClose={onClose} title="📥 Shared With Me">
      {content}
    </Modal>
  );
}

export default SharedWithMe;
