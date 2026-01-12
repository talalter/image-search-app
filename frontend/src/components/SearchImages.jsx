import React, { useState, useCallback, useRef, useEffect } from 'react';
import { searchImages as apiSearchImages } from '../utils/api';
import { useSearchHistory } from '../hooks/useSearchHistory';


function SearchImages({ selectedFolderIds }) {
  console.log('Selected folder IDs from SearchImages:', selectedFolderIds);
  const [query, setQuery] = useState('');
  const [topK, setTopK] = useState(1);
  const [results, setResults] = useState([]);
  const [error, setError] = useState('');
  const [showHistoryDropdown, setShowHistoryDropdown] = useState(false);
  const searchInputRef = useRef(null);
  const historyDropdownRef = useRef(null);

  // Search history hook
  const {
    history,
    addToHistory,
    navigateHistory,
    exitNavigation,
    isNavigating,
  } = useSearchHistory();

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (
        historyDropdownRef.current &&
        !historyDropdownRef.current.contains(event.target) &&
        searchInputRef.current &&
        !searchInputRef.current.contains(event.target)
      ) {
        setShowHistoryDropdown(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  // ✅ Memoize handleSearch to prevent recreation on every render
  const handleSearch = useCallback(async () => {
    setError('');
    setShowHistoryDropdown(false); // Hide dropdown when searching

    // Add to history when search is executed
    if (query && query.trim()) {
      addToHistory(query);
    }

    try {
      const res = await apiSearchImages(query, selectedFolderIds, topK);
      setResults(res.results || []);
    } catch (err) {
      setError(err.message || (err.details ? JSON.stringify(err.details) : 'Search failed'));
      setResults([]);
    }
  }, [query, selectedFolderIds, topK, addToHistory]); // Recreate only when these dependencies change

  // Clear all images / reset UI to initial logged-in state
  const handleClear = useCallback(() => {
    setResults([]);
    setQuery('');
    setTopK(1);
    setError('');
  }, []);

  /**
   * Handle keyboard events for search input.
   * - ArrowUp/ArrowDown: Navigate history
   * - Enter: Execute search
   * - Escape: Exit navigation and close dropdown
   */
  const handleKeyDown = useCallback((e) => {
    if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
      e.preventDefault(); // Prevent cursor movement

      const direction = e.key === 'ArrowUp' ? 'up' : 'down';
      const historyQuery = navigateHistory(direction, query);

      if (historyQuery !== null) {
        setQuery(historyQuery);
      }
    } else if (e.key === 'Enter') {
      handleSearch();
    } else if (e.key === 'Escape') {
      if (isNavigating) {
        exitNavigation();
      }
      setShowHistoryDropdown(false);
    } else if (e.key.length === 1) {
      // User started typing - exit navigation mode and show dropdown
      if (isNavigating) {
        exitNavigation();
      }
      setShowHistoryDropdown(true);
    }
  }, [query, navigateHistory, handleSearch, exitNavigation, isNavigating]);

  /**
   * Handle clicking on a history item
   */
  const handleHistoryItemClick = useCallback((historyQuery) => {
    setQuery(historyQuery);
    setShowHistoryDropdown(false);
    // Optionally auto-search:
    // handleSearch();
  }, []);

  /**
   * Handle input focus - show dropdown if there's history
   */
  const handleInputFocus = useCallback(() => {
    if (history.length > 0) {
      setShowHistoryDropdown(true);
    }
  }, [history.length]);

  return (
    <div>
      <div className="search-filters">
        <div style={{ position: 'relative', flex: 1 }}>
          <input
            ref={searchInputRef}
            type="text"
            value={query}
            placeholder="What are you looking for?"
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            onFocus={handleInputFocus}
            style={{ width: '100%' }}
          />

          {/* Search History Dropdown */}
          {showHistoryDropdown && history.length > 0 && (
            <div
              ref={historyDropdownRef}
              style={{
                position: 'absolute',
                top: '100%',
                left: 0,
                right: 0,
                backgroundColor: 'white',
                border: '1px solid #e2e8f0',
                borderRadius: '4px',
                marginTop: '4px',
                maxHeight: '300px',
                overflowY: 'auto',
                boxShadow: '0 4px 6px rgba(0, 0, 0, 0.1)',
                zIndex: 1000,
              }}
            >
              <div style={{ padding: '8px', borderBottom: '1px solid #e2e8f0', fontSize: '12px', color: '#64748b', fontWeight: '600' }}>
                Recent Searches
              </div>
              {history.map((historyQuery, index) => (
                <div
                  key={index}
                  onClick={() => handleHistoryItemClick(historyQuery)}
                  style={{
                    padding: '12px 16px',
                    cursor: 'pointer',
                    borderBottom: index < history.length - 1 ? '1px solid #f1f5f9' : 'none',
                    fontSize: '14px',
                    color: '#334155',
                    transition: 'background-color 0.15s',
                  }}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'white'}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ color: '#94a3b8', fontSize: '16px' }}>🔍</span>
                    <span>{historyQuery}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

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

export default SearchImages;