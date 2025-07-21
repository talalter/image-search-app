import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';


function Register() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const navigate = useNavigate();
  
  const handleRegister = async (e) => {
    e.preventDefault();
    setMessage('');
    setError('');

    try {
      const res = await fetch('/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      });

      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.detail || 'Registration failed');
      }

      setMessage(`User "${data.username}" registered successfully`);
      setUsername('');
      setPassword('');
      navigate('/'); // Redirect to login after successful registration
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <form onSubmit={handleRegister} autoComplete="off">
      <h2>Register</h2>
      <label>
        Username:
        <input
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          required
        />
      </label>
      <br />
      <label>
        Password:
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
      </label>
      <br />
      <button type="submit">Register</button>
      {message && <div style={{ color: 'green' }}>{message}</div>}
      {error && <div style={{ color: 'red' }}>{error}</div>}
    </form>
  );
}

export default Register;
