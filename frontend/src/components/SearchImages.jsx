import React from 'react';
import { useState } from 'react';


async function searchImages(query, folderIds, top_k = 1) {
  const token = localStorage.getItem('token');
  
  // Build query parameters for GET request
  // GET is appropriate because search is a read-only operation
  const params = new URLSearchParams({
    token,
    query,
    top_k: top_k.toString(),
  });

  // Add folder_ids as comma-separated string if provided
  // If empty, backend will search all folders
  if (folderIds.length > 0) {
    params.append('folder_ids', folderIds.join(','));
  }

  const res = await fetch(`/search-images?${params.toString()}`, {
    method: 'GET',
  });

  if (!res.ok) {
    const errorText = await res.text();
    throw new Error('Search failed: ' + errorText);
  }

  return await res.json();
}


function SearchImage({ selectedFolderIds }) {
  console.log('Selected folder IDs from SearchImages:', selectedFolderIds);
  const [query, setQuery] = useState('');
  const [topK, setTopK] = useState(1);
  const [results, setResults] = useState([]);
  const [error, setError] = useState('');

  const handleSearch = async () => {
    setError('');
    try {
      const res = await searchImages(query, selectedFolderIds, topK);
      setResults(res.results || []);
    } catch (err) {
      setError(err.message);
      setResults([]);
    }
  };

  return (
    <div>
      <div className="search-filters">
        <input
          type="text"
          value={query}
          placeholder="What are you looking for?"
          onChange={(e) => setQuery(e.target.value)}
          onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
        />
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <label style={{ fontSize: '14px', color: '#64748b' }}>Top</label>
          <input
            type="number"
            value={topK}
            min={1}
            max={50}
            onChange={(e) => setTopK(Number(e.target.value))}
          />
        </div>
        <button className="search-button" onClick={handleSearch}>
          Search
        </button>
      </div>

      {error && <div className="error-message">{error}</div>}

      {results.length > 0 && (
        <div>
          <h3 style={{ marginTop: '24px', marginBottom: '16px', color: '#2c3e50' }}>
            Found {results.length} matches
          </h3>
          <div className="results-grid">
            {results.map((item, idx) => (
              <div key={idx} className="result-item">
                <img src={item.image} alt={`match-${idx}`} />
                <div className="similarity-score">
                  {(item.similarity * 100).toFixed(1)}% match
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export default SearchImage;