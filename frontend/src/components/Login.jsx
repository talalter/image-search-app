import React from 'react';
import { useState } from 'react';

function Login({ onLogin }) {
  console.log('Login component loaded');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    console.log('Login form submitted');
    setError('');
    try {
      const res = await fetch('/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      });

      const data = await res.json();
      
      if (!res.ok) { 
        throw new Error(data.detail || 'Login failed');
      }

      localStorage.setItem('token', data.token);
      onLogin(data);
    } catch (err) {
      setError(err.message);
    }
    
  };

  return (
    <form onSubmit={handleSubmit} autoComplete="off">
      <label>
        Username:
        <input
          value={username}
          onChange={e => setUsername(e.target.value)}
          required
        />
      </label>
      <br />
      <label>
        Password:
        <input
          type="password"
          value={password}
          onChange={e => setPassword(e.target.value)}
          required
        />
      </label>
      <br />
      <button type="submit">Log In</button>
      {error && <div style={{ color: 'red' }}>{error}</div>}
    </form>
  );
}

export default Login;
