import React from 'react';
import { useState } from 'react';


async function searchImages(query, folderIds, top_k = 1) {
  const token = localStorage.getItem('token');
  const params = new URLSearchParams({
    token,
    query,
    top_k: top_k.toString(),
    folders_ids: folderIds.join(',')
  });

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
      <h2>Search Images</h2>
      <input
        type="text"
        value={query}
        placeholder="Enter search query"
        onChange={(e) => setQuery(e.target.value)}
      />
      <div style={{ marginTop: '8px' }}>
        <label>Top K results: </label>
        <input
          type="number"
          value={topK}
          min={1}
          max={50}
          style={{ width: '60px' }}
          onChange={(e) => setTopK(Number(e.target.value))}
        />
      </div>

      <button style={{ marginTop: '10px' }} onClick={handleSearch}>Search</button>

      {error && <p style={{ color: 'red' }}>{error}</p>}

      {results.length > 0 && (
        <div>
          <h3>Top Matches:</h3>
          <div style={{ display: 'flex', gap: '20px', flexWrap: 'wrap' }}>
            {results.map((item, idx) => (
              <div key={idx}>
                <p><strong>Similarity:</strong> {item.similarity.toFixed(4)}</p>
                <img src={item.image} alt={`match-${idx}-${item.image}`} style={{ maxWidth: '200px' }} />
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export default SearchImage;