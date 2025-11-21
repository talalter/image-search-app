import React from 'react';
import SearchImage from './SearchImages.jsx';
import GetFolders from './GetFolders.jsx';
import authStyles from '../styles/AppAuth.module.css';

function SearchPanel({ selectedFolderIds, setSelectedFolderIds, refreshTrigger }) {
  return (
    <>
      <SearchImage selectedFolderIds={selectedFolderIds} />
      <div className={authStyles.divider}>
        <GetFolders
          selectedFolderIds={selectedFolderIds}
          setSelectedFolderIds={setSelectedFolderIds}
          refreshTrigger={refreshTrigger}
        />
      </div>
    </>
  );
}

export default SearchPanel;
