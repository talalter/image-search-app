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
      setMessage(`Successfully uploaded ${res.uploaded_count} images.`);
      setFiles([]);
    } catch (err) {
      setMessage(`Error: ${err.message}`);
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



// function UploadImage({ user }) {
//   const [files, setFiles] = useState([]);
//   const [message, setMessage] = useState('');
//   const [dragActive, setDragActive] = useState(false);

//   const handleDrop = (e) => {
//     e.preventDefault();
//     setDragActive(false);
//     const images = Array.from(e.dataTransfer.files);
//     setFiles(images)
//     };

//     const handleDragOver = (e) => {
//         e.preventDefault();
//         setDragActive(true);
//     };  

//     const handleDragLeave = (e) => {
//         e.preventDefault();
//         setDragActive(false);
//     };

//     const handleUpload = async () => {
//     if (files.length === 0) {
//       setMessage('Please select files to upload.');
//       return;
//     }
//     try { 
//         const res = await uploadImages(files);
//         setMessage(`Successfully uploaded ${res.uploaded_count} images.`);
//         setFiles([]); // Clear files after upload
//       } catch (err) {
//         setMessage(`Error: ${err.message}`);
//       }             
//     };
      

//     return (
//         <div>
//           <h2>Upload Images</h2>
//           <div
//             onDrop={handleDrop}
//             onDragOver={handleDragOver}
//             onDragLeave={handleDragLeave}
//             style={{
//               border: '2px dashed gray',
//               padding: '30px',
//               marginBottom: '10px',
//               textAlign: 'center',
//               backgroundColor: dragActive ? '#eee' : 'white'
//             }}
//           >
//             Drag and drop images or a folder here
//             <p>{files.length > 0 && `${files.length} file(s) selected.`}</p>
//           </div>
//           <button onClick={handleUpload}>Upload</button>
//           {message && <p>{message}</p>}
//         </div>
//       );
//     }
