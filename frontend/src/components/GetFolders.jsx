import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { getFolders as apiGetFolders } from '../utils/api';

function GetFolders({selectedFolderIds, setSelectedFolderIds, refreshTrigger, variant}) {
  const [folders, setFolders] = useState([]);
  const [error, setError] = useState('');
  const [showFolders, setShowFolders] = useState(false);
  const simpleList = variant === 'listOnly';

  // Memoize fetchFolders to prevent recreation on every render
  const fetchFolders = useCallback(async () => {
    if (showFolders && !simpleList) {
      setShowFolders(false);
      return;
    }
    setError('');
    try {
  const res = await apiGetFolders();
  setFolders(res.folders);
      setShowFolders(true);
    } catch (err) {
      setError(err.message);
      setFolders([]);
    }
  }, [showFolders, simpleList]); 

  // Auto-fetch folders when component mounts OR when refreshTrigger changes
  useEffect(() => {
      const autoFetch = async () => {
      setError('');
      try {
        const res = await apiGetFolders();
        setFolders(res.folders);
        setShowFolders(true);
      } catch (err) {
        setError(err.message);
        setFolders([]);
      }
    };
    autoFetch();
  }, [refreshTrigger, simpleList]); 

  // Memoize handleClick to prevent child re-renders
  const handleClick = useCallback((folderId) => {
    setSelectedFolderIds(prev => {
      if (prev.includes(folderId)) {
        return prev.filter(id => id !== folderId);
      } else {
        return [...prev, folderId];
      }
    });
  }, [setSelectedFolderIds]); // setSelectedFolderIds is stable from useState

  // Memoize computed values for performance
  const selectedCount = useMemo(() => selectedFolderIds.length, [selectedFolderIds]);

  return (
    <div style={{ width: '100%' }}>
        {!simpleList && (
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '16px'
        }}>
          <h3 style={{ margin: 0, color: '#2c3e50' }}>
            Select Folders to Search from{selectedCount > 0 && `(${selectedCount} selected)`}
          </h3>
          <button
            onClick={fetchFolders}
            style={{
              padding: '8px 16px',
              background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
              color: 'white',
              border: 'none',
              borderRadius: '8px',
              cursor: 'pointer',
              fontSize: '14px',
              fontWeight: '500'
            }}
          >
            {showFolders ? '▲ Collapse' : '▼ Expand'}
          </button>
        </div>
      )}

      {error && (
        <p style={{ 
          color: '#c62828', 
          background: '#ffebee', 
          padding: '12px', 
          borderRadius: '8px',
          marginBottom: '12px'
        }}>
          {error}
        </p>
      )}

      {showFolders && folders.length === 0 && !error && (
        <p style={{ 
          color: '#64748b', 
          textAlign: 'center', 
          padding: '20px',
          background: '#f8fafc',
          borderRadius: '8px'
        }}>
          No folders yet. Upload some images to create folders!
        </p>
      )}

      {showFolders && folders.length > 0 && (
        <div>
          {!simpleList && (
            <p style={{
              fontSize: '14px',
              color: '#64748b',
              marginBottom: '12px',
              fontStyle: 'italic'
            }}>
              Select folders to narrow your search, or leave unselected to search all folders
            </p>
          )}
          <div style={{ 
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(250px, 1fr))',
            gap: '12px'
          }}>
            {folders.map(folder => {
              const isSelected = selectedFolderIds.includes(folder.id);
              return (
                <div
                  key={folder.id}
                  onClick={() => handleClick(folder.id)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    border: isSelected ? '2px solid #667eea' : '2px solid #e1e8ed',
                    borderRadius: '10px',
                    padding: '14px',
                    backgroundColor: isSelected ? '#f0f4ff' : 'white',
                    cursor: 'pointer',
                    transition: 'all 0.2s ease',
                    boxShadow: isSelected ? '0 4px 12px rgba(102, 126, 234, 0.2)' : '0 2px 4px rgba(0,0,0,0.05)'
                  }}
                  onMouseEnter={(e) => {
                    if (!isSelected) {
                      e.currentTarget.style.borderColor = '#cbd5e1';
                      e.currentTarget.style.transform = 'translateY(-2px)';
                      e.currentTarget.style.boxShadow = '0 4px 8px rgba(0,0,0,0.1)';
                    }
                  }}
                  onMouseLeave={(e) => {
                    if (!isSelected) {
                      e.currentTarget.style.borderColor = '#e1e8ed';
                      e.currentTarget.style.transform = 'translateY(0)';
                      e.currentTarget.style.boxShadow = '0 2px 4px rgba(0,0,0,0.05)';
                    }
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ fontSize: '20px' }}>📁</span>
                    <div>
                      <div style={{ fontWeight: '500', color: '#2c3e50' }}>
                        {folder.folder_name}
                      </div>
                      {folder.is_shared && (
                        <div style={{ 
                          fontSize: '11px',
                          color: '#4facfe',
                          fontWeight: '600',
                          marginTop: '2px'
                        }}>
                          👥 Shared folder
                        </div>
                      )}
                    </div>
                  </div>
                  {isSelected && (
                    <span style={{ 
                      fontSize: '18px',
                      color: '#667eea'
                    }}>
                      ✓
                    </span>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

export default GetFolders;