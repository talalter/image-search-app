import React from 'react';

function Card({ title, children}) {
  return (
    <div className="card">
      {title && (
        <div className="card-header">
          <h3>{title}</h3>
        </div>
      )}
      <div className="card-body">
        {children}
      </div>

    </div>
  );
}

export default Card;
