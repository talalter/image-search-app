import React, { useState, useEffect } from 'react';

function PublicFolders({ onSelectFolder }) {
  const [folders, setFolders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchPublicFolders();
  }, []);

  const fetchPublicFolders = async () => {
    setLoading(true);
    setError('');

    try {
      const res = await fetch('/public-folders', {
        method: 'GET'
      });

      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.detail || 'Failed to fetch public folders');
      }

      setFolders(data.folders);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div style={{ padding: '20px', textAlign: 'center' }}>
        <p>Loading public folders...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ padding: '20px' }}>
        <div style={{
          padding: '15px',
          backgroundColor: '#ffebee',
          color: '#c62828',
          borderRadius: '8px',
          marginBottom: '20px'
        }}>
          {error}
        </div>
        <button
          onClick={fetchPublicFolders}
          style={{
            padding: '10px 20px',
            backgroundColor: '#667eea',
            color: 'white',
            border: 'none',
            borderRadius: '8px',
            cursor: 'pointer',
            fontWeight: 'bold'
          }}
        >
          Retry
        </button>
      </div>
    );
  }

  if (folders.length === 0) {
    return (
      <div style={{ padding: '20px', textAlign: 'center' }}>
        <p style={{ color: '#666' }}>No public folders available yet.</p>
      </div>
    );
  }

  return (
    <div style={{ padding: '20px' }}>
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: '20px'
      }}>
        <h2 style={{ margin: 0 }}>📢 Public Folders</h2>
        <button
          onClick={fetchPublicFolders}
          style={{
            padding: '8px 16px',
            backgroundColor: '#667eea',
            color: 'white',
            border: 'none',
            borderRadius: '8px',
            cursor: 'pointer',
            fontSize: '14px'
          }}
        >
          🔄 Refresh
        </button>
      </div>

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
        gap: '20px'
      }}>
        {folders.map(folder => (
          <div
            key={folder.id}
            style={{
              background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
              borderRadius: '12px',
              padding: '20px',
              color: 'white',
              boxShadow: '0 4px 15px rgba(0,0,0,0.2)',
              transition: 'transform 0.2s',
              cursor: 'pointer'
            }}
            onMouseEnter={(e) => e.currentTarget.style.transform = 'translateY(-5px)'}
            onMouseLeave={(e) => e.currentTarget.style.transform = 'translateY(0)'}
            onClick={() => onSelectFolder && onSelectFolder(folder)}
          >
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'flex-start',
              marginBottom: '10px'
            }}>
              <h3 style={{ margin: 0, fontSize: '18px' }}>
                {folder.folder_name}
              </h3>
              <span style={{
                backgroundColor: 'rgba(255,255,255,0.3)',
                padding: '4px 12px',
                borderRadius: '12px',
                fontSize: '12px',
                fontWeight: 'bold'
              }}>
                {folder.image_count} images
              </span>
            </div>

            {folder.description && (
              <p style={{
                margin: '10px 0',
                fontSize: '14px',
                opacity: 0.9,
                lineHeight: '1.4'
              }}>
                {folder.description}
              </p>
            )}

            <div style={{
              marginTop: '15px',
              paddingTop: '15px',
              borderTop: '1px solid rgba(255,255,255,0.3)',
              display: 'flex',
              alignItems: 'center',
              fontSize: '14px'
            }}>
              <span style={{ opacity: 0.8 }}>👤</span>
              <span style={{ marginLeft: '8px', opacity: 0.9 }}>
                by {folder.owner_username}
              </span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default PublicFolders;
