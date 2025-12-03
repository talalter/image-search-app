import React, { useState, useEffect } from 'react';
import { getJobStatus } from '../utils/api';
import '../styles/components/uploadProgress.css';

/**
 * Upload Progress Component
 *
 * Displays real-time progress of embedding job processing
 * Polls the job status endpoint every 2 seconds until completion
 *
 * @param {number} jobId - Job ID from upload response
 * @param {string} token - User session token
 * @param {function} onComplete - Callback when job completes (optional)
 */
export default function UploadProgress({ jobId, token, onComplete }) {
  const [status, setStatus] = useState(null);
  const [error, setError] = useState(null);
  const [polling, setPolling] = useState(true);

  useEffect(() => {
    if (!jobId || !token) {
      setError('Invalid job ID or token');
      return;
    }

    let interval = null;

    const pollStatus = async () => {
      try {
        const data = await getJobStatus(jobId, token);
        setStatus(data);
        setError(null);

        // Stop polling if job is completed or failed
        if (data.status === 'COMPLETED' || data.status === 'FAILED') {
          setPolling(false);
          if (interval) clearInterval(interval);
          if (onComplete) onComplete(data);
        }
      } catch (err) {
        console.error('Failed to fetch job status:', err);
        setError(err.message || 'Failed to fetch job status');
        setPolling(false);
        if (interval) clearInterval(interval);
      }
    };

    // Initial fetch
    pollStatus();

    // Poll every 2 seconds
    if (polling) {
      interval = setInterval(pollStatus, 2000);
    }

    // Cleanup
    return () => {
      if (interval) {
        clearInterval(interval);
      }
    };
  }, [jobId, token, polling, onComplete]);

  if (error) {
    return (
      <div className="upload-progress error">
        <p className="error-message">Error: {error}</p>
      </div>
    );
  }

  if (!status) {
    return (
      <div className="upload-progress loading">
        <p>Loading job status...</p>
      </div>
    );
  }

  const getStatusClass = () => {
    switch (status.status) {
      case 'COMPLETED':
        return 'success';
      case 'FAILED':
        return 'error';
      case 'PROCESSING':
        return 'processing';
      case 'PENDING':
        return 'pending';
      default:
        return '';
    }
  };

  const getStatusEmoji = () => {
    switch (status.status) {
      case 'COMPLETED':
        return '✅';
      case 'FAILED':
        return '❌';
      case 'PROCESSING':
        return '⚙️';
      case 'PENDING':
        return '⏳';
      default:
        return '';
    }
  };

  return (
    <div className={`upload-progress ${getStatusClass()}`}>
      <div className="progress-header">
        <h3>
          {getStatusEmoji()} Embedding Progress
        </h3>
        <span className="job-id">Job #{status.job_id}</span>
      </div>

      <div className="progress-bar-container">
        <div
          className="progress-bar-fill"
          style={{ width: `${status.progress_percentage || 0}%` }}
        >
          {status.progress_percentage > 10 && (
            <span className="progress-text">{status.progress_percentage.toFixed(1)}%</span>
          )}
        </div>
      </div>

      <div className="progress-details">
        <p className="status-line">
          <strong>Status:</strong> {status.status}
        </p>
        <p className="progress-line">
          <strong>Progress:</strong> {status.processed_images}/{status.total_images} images
        </p>
      </div>

      {status.status === 'FAILED' && status.error_message && (
        <div className="error-details">
          <p className="error-message">{status.error_message}</p>
        </div>
      )}

      {status.status === 'COMPLETED' && (
        <div className="success-message">
          <p>All images embedded successfully! You can now search your images.</p>
        </div>
      )}

      {polling && (status.status === 'PENDING' || status.status === 'PROCESSING') && (
        <div className="polling-indicator">
          <span className="pulse"></span>
          <span>Updating...</span>
        </div>
      )}
    </div>
  );
}
