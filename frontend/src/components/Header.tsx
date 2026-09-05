import { useState, useEffect } from 'react';
import { Activity, Wifi, WifiOff, LogOut, User } from 'lucide-react';
import { healthCheck } from '../api/client';
import { useAuth } from '../context/AuthContext';

export default function Header() {
  const { user, logout } = useAuth();
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    let mounted = true;

    const check = async () => {
      try {
        await healthCheck();
        if (mounted) setConnected(true);
      } catch {
        if (mounted) setConnected(false);
      }
    };

    check();
    const interval = setInterval(check, 15000);
    return () => {
      mounted = false;
      clearInterval(interval);
    };
  }, []);

  return (
    <header className="header">
      <div className="header-left">
        <div className="header-logo">
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="white"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
            <path d="M14 2v6h6" />
            <path d="M9 15h6" />
            <path d="M9 11h6" />
            <circle cx="17" cy="18" r="4" fill="rgba(6,182,212,0.3)" stroke="rgba(6,182,212,0.8)" strokeWidth="1.5" />
            <path d="M16 17.5l.7.7 1.3-1.4" stroke="rgba(6,182,212,0.9)" strokeWidth="1.5" />
          </svg>
        </div>
        <h1 className="header-title">
          <span className="gradient-text">OmniDoc AI</span>
        </h1>
      </div>

      <div className="header-right">
        <div className="header-status">
          {connected ? (
            <>
              <span className="status-dot status-dot-green" />
              <Wifi size={14} />
              <span>Connected</span>
            </>
          ) : (
            <>
              <span className="status-dot status-dot-red" />
              <WifiOff size={14} />
              <span>Disconnected</span>
            </>
          )}
        </div>

        {user && (
          <div className="header-user">
            <div className="header-user-avatar">
              <User size={14} />
            </div>
            <span className="header-user-name">{user.name}</span>
            <button
              className="btn btn-ghost btn-icon header-logout"
              title="Logout"
              onClick={logout}
            >
              <LogOut size={16} />
            </button>
          </div>
        )}

        <button className="btn btn-ghost btn-icon" title="Activity">
          <Activity size={18} />
        </button>
      </div>
    </header>
  );
}
