import React, { useState, useEffect } from 'react';
import { getFoldersSharedWithMe } from '../utils/api';

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

  const Inner = (
    <div style={{
      background: 'white',
      borderRadius: '16px',
      padding: '24px',
      maxWidth: '600px',
      width: '100%',
      maxHeight: '80vh',
      overflow: 'auto',
      boxShadow: '0 20px 60px rgba(0, 0, 0, 0.1)'
    }}>
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: '16px'
      }}>
        <h2 style={{ margin: 0, color: '#2c3e50', fontSize: '20px' }}>📥 Shared With Me</h2>
        {!inline && (
          <button
            onClick={onClose}
            style={{ background: 'none', border: 'none', fontSize: '20px', cursor: 'pointer', color: '#64748b', padding: '0 8px' }}
          >
            ×
          </button>
        )}
      </div>

      {loading && (
        <div style={{ textAlign: 'center', padding: '12px', color: '#64748b' }}>Loading...</div>
      )}

      {error && (
        <div style={{ padding: '12px 16px', backgroundColor: '#fee', color: '#c33', borderRadius: '8px', marginBottom: '12px', fontSize: '14px', borderLeft: '4px solid #e74c3c' }}>{error}</div>
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

      {inline ? null : (
        <button onClick={onClose} style={{ width: '100%', padding: '12px', background: '#e2e8f0', color: '#475569', border: 'none', borderRadius: '10px', fontSize: '16px', fontWeight: '600', cursor: 'pointer', marginTop: '12px' }}>Close</button>
      )}
    </div>
  );

  if (inline) return Inner;

  return (
    <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000 }}>
      {Inner}
    </div>
  );
}

export default SharedWithMe;
