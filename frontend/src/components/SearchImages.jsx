import React, { useState, useCallback } from 'react';
import { searchImages as apiSearchImages } from '../utils/api';


function SearchImage({ selectedFolderIds }) {
  console.log('Selected folder IDs from SearchImages:', selectedFolderIds);
  const [query, setQuery] = useState('');
  const [topK, setTopK] = useState(1);
  const [results, setResults] = useState([]);
  const [error, setError] = useState('');

  // ✅ Memoize handleSearch to prevent recreation on every render
  const handleSearch = useCallback(async () => {
    setError('');
    try {
      const res = await apiSearchImages(query, selectedFolderIds, topK);
      setResults(res.results || []);
    } catch (err) {
      setError(err.message || (err.details ? JSON.stringify(err.details) : 'Search failed'));
      setResults([]);
    }
  }, [query, selectedFolderIds, topK]); // Recreate only when these dependencies change

  // Clear all images / reset UI to initial logged-in state
  const handleClear = useCallback(() => {
    setResults([]);
    setQuery('');
    setTopK(1);
    setError('');
  }, []);

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
        <button
          className="search-button"
          onClick={handleClear}
        >
          Clear
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