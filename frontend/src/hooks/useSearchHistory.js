import { useState, useEffect, useCallback, useRef } from 'react';
import { getSearchHistory, addSearchHistory } from '../utils/api';

/**
 * Custom hook for managing search history with keyboard navigation.
 *
 * Features:
 * - Loads history from backend on mount
 * - Optimistic updates (immediate local state update + background sync)
 * - Arrow key navigation (Up/Down)
 * - Escape key to exit navigation
 * - LRU behavior (automatic via backend)
 *
 * @returns {Object} Hook interface
 */
export function useSearchHistory() {
  // State
  const [history, setHistory] = useState([]); // Array of query strings
  const [currentIndex, setCurrentIndex] = useState(-1); // -1 = not navigating
  const [isNavigating, setIsNavigating] = useState(false);
  const [originalInput, setOriginalInput] = useState(''); // User's input before navigation

  // Ref for cleanup
  const isMountedRef = useRef(true);

  /**
   * Fetch history from backend on mount.
   */
  useEffect(() => {
    const fetchHistory = async () => {
      try {
        const response = await getSearchHistory();
        if (isMountedRef.current) {
          setHistory(response.queries || []);
        }
      } catch (error) {
        console.error('Failed to fetch search history:', error);
        // Fail silently - history is optional feature
      }
    };

    fetchHistory();

    // Cleanup on unmount
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  /**
   * Add query to history immediately (no debounce).
   * Optimistically updates local state while API call is in flight.
   *
   * @param {string} query - Query to add
   */
  const addToHistory = useCallback((query) => {
    if (!query || query.trim().length === 0) {
      return;
    }

    const trimmedQuery = query.trim();

    // Optimistically add to local state immediately (avoid duplicates)
    setHistory(prevHistory => {
      // If query already exists, move it to the front
      const filtered = prevHistory.filter(q => q !== trimmedQuery);
      return [trimmedQuery, ...filtered];
    });

    // Send API request to persist to backend (no debounce)
    addSearchHistory(trimmedQuery)
      .then(response => {
        if (isMountedRef.current) {
          // Update with backend response to ensure consistency
          setHistory(response.queries || []);
        }
      })
      .catch(error => {
        console.error('Failed to add to search history:', error);
        // Keep optimistic update even if API fails
        // User will still see history in current session
      });
  }, []);

  /**
   * Navigate through history using arrow keys.
   *
   * @param {string} direction - 'up' or 'down'
   * @param {string} currentValue - Current input value
   * @returns {string|null} Query to display, or null if no change
   */
  const navigateHistory = useCallback((direction, currentValue) => {
    if (history.length === 0) {
      return null;
    }

    let newIndex = currentIndex;

    if (direction === 'up') {
      // Move backward in history (older queries)
      if (!isNavigating) {
        // Just started navigating - save current input and go to first item
        setOriginalInput(currentValue);
        setIsNavigating(true);
        newIndex = 0;
      } else if (currentIndex < history.length - 1) {
        // Not at oldest yet - move to next older
        newIndex = currentIndex + 1;
      } else {
        // Already at oldest - stay there
        return null;
      }
    } else if (direction === 'down') {
      // Move forward in history (newer queries)
      if (!isNavigating) {
        // Not navigating - do nothing
        return null;
      } else if (currentIndex > 0) {
        // Not at newest yet - move to next newer
        newIndex = currentIndex - 1;
      } else {
        // At newest - exit navigation and restore original input
        setIsNavigating(false);
        setCurrentIndex(-1);
        return originalInput;
      }
    }

    setCurrentIndex(newIndex);
    return history[newIndex];
  }, [history, currentIndex, isNavigating, originalInput]);

  /**
   * Exit navigation mode (e.g., on Escape or when user starts typing).
   */
  const exitNavigation = useCallback(() => {
    setIsNavigating(false);
    setCurrentIndex(-1);
    setOriginalInput('');
  }, []);

  /**
   * Reset navigation state (useful when input is cleared).
   */
  const resetNavigation = useCallback(() => {
    setIsNavigating(false);
    setCurrentIndex(-1);
    setOriginalInput('');
  }, []);

  return {
    history,
    currentIndex,
    isNavigating,
    addToHistory,
    navigateHistory,
    exitNavigation,
    resetNavigation,
  };
}
