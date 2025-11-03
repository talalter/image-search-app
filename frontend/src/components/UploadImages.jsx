import React, { useState } from 'react';

async function uploadImages(files, folderName) {
  const formData = new FormData();
  const token = localStorage.getItem('token');

  formData.append('token', token);
  formData.append('folderName', folderName);
  //formData.append('isNewFolder', isNewFolder.toString());
  files.forEach(file => formData.append('files', file));

  const res = await fetch('/upload-images', {
    method: 'POST',
    body: formData,
  });

  if (!res.ok) {
    const errorText = await res.text();
    throw new Error('Image upload failed: ' + errorText);
  }
  return await res.json();
}

function UploadImages({mode}) {
  const [files, setFiles] = useState([]);
  const [folderName, setFolderName] = useState('');
  const [message, setMessage] = useState('');

  const handleFileChange = (e) => {
    const selectedFiles = Array.from(e.target.files);
    
    // Check if files were actually selected
    if (selectedFiles.length === 0) {
      return; // User cancelled or didn't select anything
    }
    
    // Get folder name from first file's path
    const firstPath = selectedFiles[0].webkitRelativePath.split('/')[0];
    setFolderName(firstPath);
    setFiles(selectedFiles);
  };

  const handleUpload = async () => {
    if (files.length === 0) {
      setMessage('Please select files to upload.');
      return;
    }
    const isNewFolder = mode === "upload_new";
    try {
      const res = await uploadImages(files, folderName, isNewFolder);
      setMessage(`✅ Successfully uploaded ${res.uploaded_count} images.`);
      setFiles([]);
      setFolderName('');
    } catch (err) {
      // Make error messages more user-friendly
      let errorMsg = err.message;
      if (errorMsg.includes('UNIQUE constraint')) {
        errorMsg = `❌ Folder "${folderName}" already exists. Please rename your folder or use "Expand Existing Folder" to add more images.`;
      } else if (errorMsg.includes('FOREIGN KEY')) {
        errorMsg = `❌ Database error. Please try again.`;
      }
      setMessage(errorMsg);
    }
  };

  return (
    <div>
      <h2>Upload Folder</h2>

      <input
        type="file"
        webkitdirectory="true"
        directory=""
        multiple
        onChange={handleFileChange}
      />
      <p>{files.length > 0 && `${files.length} file(s) selected.`}</p>

      <button onClick={handleUpload}>Upload</button>

      {message && <p>{message}</p>}
    </div>
  );
}

export default UploadImages;


