import React from 'react';

const UniMageLogo = ({ size = 80 }) => {
  return (
    <div style={{ textAlign: 'center', marginBottom: '24px' }}>
      <svg
        width={size}
        height={size}
        viewBox="0 0 100 100"
        xmlns="http://www.w3.org/2000/svg"
        style={{ marginBottom: '12px' }}
      >
        {/* Outer circle with gradient */}
        <defs>
          <linearGradient id="logoGradient" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" style={{ stopColor: '#667eea', stopOpacity: 1 }} />
            <stop offset="100%" style={{ stopColor: '#764ba2', stopOpacity: 1 }} />
          </linearGradient>
          
          <linearGradient id="innerGradient" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" style={{ stopColor: '#f093fb', stopOpacity: 1 }} />
            <stop offset="100%" style={{ stopColor: '#f5576c', stopOpacity: 1 }} />
          </linearGradient>
        </defs>
        
        {/* Background circle */}
        <circle cx="50" cy="50" r="48" fill="url(#logoGradient)" opacity="0.1" />
        
        {/* Main circle border */}
        <circle 
          cx="50" 
          cy="50" 
          r="45" 
          fill="none" 
          stroke="url(#logoGradient)" 
          strokeWidth="3"
        />
        
        {/* Image frame icon */}
        <rect x="25" y="30" width="50" height="40" rx="4" fill="none" stroke="url(#logoGradient)" strokeWidth="3" />
        
        {/* Mountain/landscape inside frame */}
        <path 
          d="M 30 60 L 40 45 L 50 55 L 65 35 L 70 60 Z" 
          fill="url(#innerGradient)"
          opacity="0.8"
        />
        
        {/* Sun/circle in corner */}
        <circle cx="60" cy="40" r="5" fill="#ffd700" />
        
        {/* Search magnifier overlay */}
        <circle 
          cx="70" 
          cy="70" 
          r="8" 
          fill="white" 
          stroke="url(#logoGradient)" 
          strokeWidth="3"
        />
        <line 
          x1="76" 
          y1="76" 
          x2="83" 
          y2="83" 
          stroke="url(#logoGradient)" 
          strokeWidth="3" 
          strokeLinecap="round"
        />
      </svg>
      
      <div style={{
        fontSize: '32px',
        fontWeight: '700',
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        WebkitBackgroundClip: 'text',
        WebkitTextFillColor: 'transparent',
        backgroundClip: 'text',
        letterSpacing: '-0.5px'
      }}>
      </div>
      
      <div style={{
        fontSize: '13px',
        color: '#64748b',
        marginTop: '4px',
        fontWeight: '500'
      }}>
      </div>
    </div>
  );
};

export default UniMageLogo;
