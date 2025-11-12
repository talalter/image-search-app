import React, {useEffect, useState, useCallback, useMemo } from 'react';

// Upload files in batches for better performance
async function uploadImagesInBatches(files, folderName, onProgress) {
  const BATCH_SIZE = 20; // Upload 20 images at a time
  const token = localStorage.getItem('token');
  let totalUploaded = 0;

  for (let i = 0; i < files.length; i += BATCH_SIZE) {
    const batch = files.slice(i, i + BATCH_SIZE);
    const formData = new FormData();
    
    formData.append('token', token);
    formData.append('folderName', folderName);
    batch.forEach(file => formData.append('files', file));

    const res = await fetch('/upload-images', {
      method: 'POST',
      body: formData,
    });

    if (!res.ok) {
      const errorText = await res.text();
      throw new Error('Image upload failed: ' + errorText);
    }
    
    const result = await res.json();
    totalUploaded += result.uploaded_count;
    
    // Update progress
    if (onProgress) {
      onProgress(totalUploaded, files.length);
    }
  }

  return { uploaded_count: totalUploaded };
}

function UploadImages({folderId, onUploadSuccess}) {
  const [files, setFiles] = useState([]);
  const [folderName, setFolderName] = useState('');
  const [message, setMessage] = useState('');
  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState({ current: 0, total: 0 });
  const [existingFolderName, setExistingFolderName] = useState('');

  // Fetch folder name when folderId is provided (for adding to existing folder)
  useEffect(() => {
    if (folderId) {
      const fetchFolderInfo = async () => {
        try {
          const token = localStorage.getItem('token');
          const response = await fetch(`/get-folders?token=${token}`);
          if (response.ok) {
            const data = await response.json();
            const folder = data.folders.find(f => f.id === parseInt(folderId));
            if (folder) {
              setExistingFolderName(folder.folder_name);
              setFolderName(folder.folder_name); // Set this as the upload target
            }
          }
        } catch (error) {
          console.error('Error fetching folder info:', error);
        }
      };
      fetchFolderInfo();
    }
  }, [folderId]);

  // Memoize file change handler
  const handleFileChange = useCallback((e) => {
    const selectedFiles = Array.from(e.target.files);
    
    // Check if files were actually selected
    if (selectedFiles.length === 0) {
      return; // User cancelled or didn't select anything
    }
    
    // Only set folder name from file path if we're not adding to existing folder
    if (!folderId) {
      const firstPath = selectedFiles[0].webkitRelativePath.split('/')[0];
      setFolderName(firstPath);
    }
    setFiles(selectedFiles);
  }, [folderId]);

  // Memoize drag handlers to prevent recreation
  const handleDragEnter = useCallback((e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  }, []);

  const handleDragLeave = useCallback((e) => {
    e.preventDefault();
    e.stopPropagation();
    // Only set to false if leaving the drop zone entirely
    if (e.currentTarget === e.target) {
      setIsDragging(false);
    }
  }, []);

  const handleDragOver = useCallback((e) => {
    e.preventDefault();
    e.stopPropagation();
  }, []);

  const handleDrop = async (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);

    const items = e.dataTransfer.items;
    if (!items || items.length === 0) return;

    const filesList = [];
    let folderPath = '';

    // Process all dropped items
    for (let i = 0; i < items.length; i++) {
      const item = items[i].webkitGetAsEntry();
      if (item) {
        if (item.isDirectory) {
          // Get folder name
          if (!folderPath) folderPath = item.name;
          await traverseDirectory(item, '', filesList);
        } else if (item.isFile) {
          const file = items[i].getAsFile();
          if (file) filesList.push(file);
        }
      }
    }

    if (filesList.length > 0) {
      setFiles(filesList);
      // Only set folder name if we're not adding to existing folder
      if (!folderId) {
        setFolderName(folderPath || 'uploaded-folder');
      }
    }
  };

  // Recursively traverse directory structure (OPTIMIZED - parallel processing)
  const traverseDirectory = async (entry, path, filesList) => {
    if (entry.isFile) {
      return new Promise((resolve) => {
        entry.file((file) => {
          // Add path information to file
          const newFile = new File([file], file.name, { type: file.type });
          Object.defineProperty(newFile, 'webkitRelativePath', {
            value: path + file.name,
            writable: false
          });
          filesList.push(newFile);
          resolve();
        });
      });
    } else if (entry.isDirectory) {
      const dirReader = entry.createReader();
      return new Promise((resolve) => {
        dirReader.readEntries(async (entries) => {
          // Process all entries in parallel instead of sequentially
          await Promise.all(
            entries.map(entry => traverseDirectory(entry, path + entry.name + '/', filesList))
          );
          resolve();
        });
      });
    }
  };

  // Memoize progress update callback to prevent recreation
  const updateProgress = useCallback((current, total) => {
    setUploadProgress({ current, total });
  }, []);

  // Memoize upload handler
  const handleUpload = useCallback(async () => {
    if (files.length === 0) {
      setMessage('Please select files to upload.');
      return;
    }

    setIsUploading(true);
    
    try {
      const res = await uploadImagesInBatches(
        files, 
        folderName, 
        updateProgress
      );
      setMessage(`Successfully uploaded ${res.uploaded_count} images.`);
      setFiles([]);
      setFolderName('');
      setUploadProgress({ current: 0, total: 0 });
      
      // Reset file input
      const fileInput = document.querySelector('input[type="file"]');
      if (fileInput) fileInput.value = '';
      
      // Notify parent component that upload was successful
      if (onUploadSuccess) {
        onUploadSuccess();
      }
    } catch (err) {
      // Make error messages more user-friendly
      let errorMsg = err.message;
      if (errorMsg.includes('UNIQUE constraint')) {
        errorMsg = `❌ Folder "${folderName}" already exists. Please rename your folder or use "Expand Existing Folder" to add more images.`;
      } else if (errorMsg.includes('FOREIGN KEY')) {
        errorMsg = `❌ Database error. Please try again.`;
      }
      setMessage(errorMsg);
    } finally {
      setIsUploading(false);
    }
  }, [files, folderName, updateProgress, onUploadSuccess]);

  // Memoize computed values
  const hasFiles = useMemo(() => files.length > 0, [files.length]);
  const isAddingToExisting = useMemo(() => Boolean(folderId), [folderId]);

  return (
    <div>
      {/* Drag and Drop Zone */}
      <div
        onDragEnter={handleDragEnter}
        onDragLeave={handleDragLeave}
        onDragOver={handleDragOver}
        onDrop={handleDrop}
        style={{
          border: isDragging ? '3px dashed #8b5cf6' : '2px dashed #d1d5db',
          borderRadius: '12px',
          padding: '40px 20px',
          textAlign: 'center',
          backgroundColor: isDragging ? '#f3e8ff' : '#f9fafb',
          transition: 'all 0.3s ease',
          cursor: 'pointer',
          marginBottom: '20px'
        }}
      >
        <div style={{ fontSize: '48px', marginBottom: '10px' }}>
          {isDragging ? '📂' : '📁'}
        </div>
        <h3 style={{ 
          margin: '10px 0',
          background: 'linear-gradient(135deg, #8b5cf6 0%, #ec4899 100%)',
          WebkitBackgroundClip: 'text',
          WebkitTextFillColor: 'transparent',
          backgroundClip: 'text'
        }}>
          {isAddingToExisting 
            ? (isDragging ? `Drop images for "${existingFolderName}"` : `Add Images to "${existingFolderName}"`)
            : (isDragging ? 'Drop folder here' : 'Drag & Drop Folder Here')
          }
        </h3>
        <p style={{ color: '#6b7280', marginBottom: '15px' }}>
          or
        </p>
        
        {/* Hidden file input */}
        <input
          type="file"
          id="folder-input"
          webkitdirectory={isAddingToExisting ? "" : "true"}
          directory={isAddingToExisting ? "" : ""}
          multiple
          accept={isAddingToExisting ? "image/*" : ""}
          onChange={handleFileChange}
          style={{ display: 'none' }}
        />
        
        {/* Custom styled button */}
        <label 
          htmlFor="folder-input"
          style={{
            display: 'inline-block',
            padding: '12px 24px',
            background: 'linear-gradient(135deg, #8b5cf6 0%, #ec4899 100%)',
            color: 'white',
            borderRadius: '8px',
            cursor: 'pointer',
            fontWeight: '600',
            transition: 'transform 0.2s ease',
            border: 'none'
          }}
          onMouseOver={(e) => e.target.style.transform = 'scale(1.05)'}
          onMouseOut={(e) => e.target.style.transform = 'scale(1)'}
        >
          {isAddingToExisting ? '📷 Browse for Images' : '📁 Browse for Folder'}
        </label>
      </div>

      {/* Selected files info */}
      {hasFiles && (
        <div style={{
          background: 'linear-gradient(135deg, #ede9fe 0%, #fce7f3 100%)',
          padding: '15px',
          borderRadius: '8px',
          marginBottom: '15px',
          border: '1px solid #e9d5ff'
        }}>
          <p style={{ margin: '5px 0', fontWeight: '600', color: '#7c3aed' }}>
            📂 Folder: {folderName}
          </p>
          <p style={{ margin: '5px 0', color: '#a855f7' }}>
            📊 {files.length} file(s) selected
          </p>
        </div>
      )}

      {/* Upload button */}
      {hasFiles && (
        <button 
          onClick={handleUpload}
          disabled={isUploading}
          style={{
            width: '100%',
            padding: '14px',
            background: isUploading 
              ? 'linear-gradient(135deg, #9ca3af 0%, #6b7280 100%)'
              : 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
            color: 'white',
            border: 'none',
            borderRadius: '8px',
            fontSize: '16px',
            fontWeight: '600',
            cursor: isUploading ? 'not-allowed' : 'pointer',
            transition: 'all 0.3s ease',
            marginBottom: '15px'
          }}
          onMouseOver={(e) => {
            if (!isUploading) e.target.style.transform = 'translateY(-2px)';
          }}
          onMouseOut={(e) => {
            if (!isUploading) e.target.style.transform = 'translateY(0)';
          }}
        >
          {isUploading ? '⏳ Uploading...' : '🚀 Upload Images'}
        </button>
      )}

      {/* Progress bar */}
      {isUploading && uploadProgress.total > 0 && (
        <div style={{
          marginBottom: '15px',
          background: '#f3f4f6',
          borderRadius: '8px',
          overflow: 'hidden',
          border: '1px solid #e5e7eb'
        }}>
          <div style={{
            height: '30px',
            background: 'linear-gradient(135deg, #8b5cf6 0%, #ec4899 100%)',
            width: `${(uploadProgress.current / uploadProgress.total) * 100}%`,
            transition: 'width 0.3s ease',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: 'white',
            fontWeight: '600',
            fontSize: '14px'
          }}>
            {uploadProgress.current} / {uploadProgress.total}
          </div>
        </div>
      )}

      {/* Message display */}
      {message && (
        <div style={{
          padding: '12px',
          borderRadius: '8px',
          marginTop: '10px',
          backgroundColor: message.includes('Successfully') ? '#d1fae5' : '#fee2e2',
          border: message.includes('Successfully') ? '1px solid #6ee7b7' : '1px solid #fca5a5',
          color: message.includes('Successfully') ? '#065f46' : '#991b1b'
        }}>
          {message}
        </div>
      )}
    </div>
  );
}

export default UploadImages;


