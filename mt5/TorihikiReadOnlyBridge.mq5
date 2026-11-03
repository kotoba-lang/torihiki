//+------------------------------------------------------------------+
//| TorihikiReadOnlyBridge.mq5                                       |
//| Read-only bridge for torihiki. NOT COMPILED, NOT RUN against a   |
//| real terminal (see docs/mt5-readonly-bridge.md, "Unverified").   |
//|                                                                  |
//| - Runs only on a DEMO account.                                   |
//| - Connects only to this machine (127.0.0.1 / localhost).         |
//| - Sends data out; never reads from the socket; contains no       |
//|   trading call. scripts/check-ea-readonly.sh enforces that.      |
//+------------------------------------------------------------------+
#property strict
#property version   "0.1"
#property description "torihiki read-only bridge (demo only)"

input string InpHost       = "127.0.0.1"; // loopback only
input int    InpPort       = 8765;
input string InpSymbols    = "EURUSD";    // comma separated, as named by the broker
input int    InpIntervalMs = 1000;

int    g_sock = INVALID_HANDLE;
long   g_n    = 0;
string g_syms[];

//--- JSON string literal (symbol names and currency only; escape \ and ")
string JStr(const string s)
  {
   string o = s;
   StringReplace(o, "\\", "\\\\");
   StringReplace(o, "\"", "\\\"");
   return "\"" + o + "\"";
  }

//--- every frame carries a strictly increasing n within a connection
string Head(const string t)
  {
   g_n++;
   return "{\"t\":" + JStr(t) + ",\"n\":" + IntegerToString(g_n);
  }

bool SendLine(const string body)
  {
   if(g_sock == INVALID_HANDLE) return false;
   uchar buf[];
   int len = StringToCharArray(body + "}\n", buf, 0, WHOLE_ARRAY, CP_UTF8) - 1; // drop terminator
   if(len <= 0) return false;
   return SocketSend(g_sock, buf, (uint)len) == len;
  }

//--- decimals are sent as strings with the symbol's own digit count, so the
//    consumer never sees a binary float
string Px(const double v, const int digits) { return JStr(DoubleToString(v, digits)); }

int VolDigits(const double step)
  {
   int d = 0;
   double s = step;
   while(d < 8 && MathAbs(s - MathRound(s)) > 1e-9) { s *= 10.0; d++; }
   return d;
  }

string TradeMode(const string sym)
  {
   switch((int)SymbolInfoInteger(sym, SYMBOL_TRADE_MODE))
     {
      case SYMBOL_TRADE_MODE_FULL:      return "full";
      case SYMBOL_TRADE_MODE_LONGONLY:  return "longonly";
      case SYMBOL_TRADE_MODE_SHORTONLY: return "shortonly";
      case SYMBOL_TRADE_MODE_CLOSEONLY: return "closeonly";
      default:                          return "disabled";
     }
  }

bool SendSym(const string sym)
  {
   int    digits = (int)SymbolInfoInteger(sym, SYMBOL_DIGITS);
   double step   = SymbolInfoDouble(sym, SYMBOL_VOLUME_STEP);
   int    vd     = VolDigits(step);
   double tick   = SymbolInfoDouble(sym, SYMBOL_TRADE_TICK_SIZE);
   return SendLine(Head("sym") + ",\"s\":" + JStr(sym)
                   + ",\"digits\":" + IntegerToString(digits)
                   + ",\"step\":" + JStr(DoubleToString(step, vd))
                   + ",\"min\":"  + JStr(DoubleToString(SymbolInfoDouble(sym, SYMBOL_VOLUME_MIN), vd))
                   + ",\"max\":"  + JStr(DoubleToString(SymbolInfoDouble(sym, SYMBOL_VOLUME_MAX), vd))
                   + ",\"tick\":" + JStr(DoubleToString(tick, digits))
                   + ",\"trade\":" + JStr(TradeMode(sym)));
  }

bool SendTick(const string sym)
  {
   MqlTick t;
   if(!SymbolInfoTick(sym, t)) return true;       // no tick yet: say nothing
   if(t.bid <= 0.0 || t.ask <= 0.0) return true;
   int digits = (int)SymbolInfoInteger(sym, SYMBOL_DIGITS);
   return SendLine(Head("tick") + ",\"s\":" + JStr(sym)
                   + ",\"bid\":" + Px(t.bid, digits)
                   + ",\"ask\":" + Px(t.ask, digits));
  }

bool SendAccount()
  {
   return SendLine(Head("acct")
                   + ",\"balance\":" + JStr(DoubleToString(AccountInfoDouble(ACCOUNT_BALANCE), 2))
                   + ",\"equity\":"  + JStr(DoubleToString(AccountInfoDouble(ACCOUNT_EQUITY), 2))
                   + ",\"margin\":"  + JStr(DoubleToString(AccountInfoDouble(ACCOUNT_MARGIN), 2))
                   + ",\"free\":"    + JStr(DoubleToString(AccountInfoDouble(ACCOUNT_MARGIN_FREE), 2))
                   + ",\"ccy\":"     + JStr(AccountInfoString(ACCOUNT_CURRENCY)));
  }

//--- the whole list is bracketed so the consumer replaces its copy atomically
bool SendPositions()
  {
   if(!SendLine(Head("pos-begin"))) return false;
   int total = PositionsTotal();
   for(int i = 0; i < total; i++)
     {
      ulong ticket = PositionGetTicket(i);       // selects the position
      if(ticket == 0) continue;
      string sym  = PositionGetString(POSITION_SYMBOL);
      long   type = PositionGetInteger(POSITION_TYPE);
      double vol  = PositionGetDouble(POSITION_VOLUME);
      double step = SymbolInfoDouble(sym, SYMBOL_VOLUME_STEP);
      string side = (type == POSITION_TYPE_BUY) ? "buy" : "sell";
      if(!SendLine(Head("pos") + ",\"s\":" + JStr(sym) + ",\"type\":" + JStr(side)
                   + ",\"vol\":" + JStr(DoubleToString(vol, VolDigits(step)))))
         return false;
     }
   return SendLine(Head("pos-end"));
  }

void Disconnect()
  {
   if(g_sock != INVALID_HANDLE) { SocketClose(g_sock); g_sock = INVALID_HANDLE; }
  }

bool Connect()
  {
   g_sock = SocketCreate();
   if(g_sock == INVALID_HANDLE) return false;
   if(!SocketConnect(g_sock, InpHost, (uint)InpPort, 1000)) { Disconnect(); return false; }
   g_n = 0;                                       // a new connection restarts n
   string mode = "other";
   if(AccountInfoInteger(ACCOUNT_TRADE_MODE) == ACCOUNT_TRADE_MODE_DEMO) mode = "demo";
   if(!SendLine("{\"t\":\"hello\",\"v\":1,\"n\":0,\"mode\":" + JStr(mode) + "}")) { Disconnect(); return false; }
   for(int i = 0; i < ArraySize(g_syms); i++)
      if(!SendSym(g_syms[i])) { Disconnect(); return false; }
   return true;
  }

int OnInit()
  {
   if(AccountInfoInteger(ACCOUNT_TRADE_MODE) != ACCOUNT_TRADE_MODE_DEMO)
     {
      Print("torihiki bridge: refusing to run, this is not a demo account");
      return INIT_FAILED;
     }
   if(InpHost != "127.0.0.1" && InpHost != "localhost")
     {
      Print("torihiki bridge: refusing a non-loopback host");
      return INIT_PARAMETERS_INCORRECT;
     }
   if(InpPort < 1024 || InpPort > 65535 || InpIntervalMs < 250)
      return INIT_PARAMETERS_INCORRECT;
   int n = StringSplit(InpSymbols, ',', g_syms);
   if(n <= 0) return INIT_PARAMETERS_INCORRECT;
   for(int i = 0; i < n; i++)
     {
      StringTrimLeft(g_syms[i]);
      StringTrimRight(g_syms[i]);
      if(!SymbolSelect(g_syms[i], true))
        {
         Print("torihiki bridge: unknown symbol ", g_syms[i]);
         return INIT_PARAMETERS_INCORRECT;
        }
     }
   EventSetMillisecondTimer(InpIntervalMs);
   return INIT_SUCCEEDED;
  }

void OnDeinit(const int reason)
  {
   EventKillTimer();
   Disconnect();
  }

void OnTimer()
  {
   if(g_sock == INVALID_HANDLE && !Connect()) return;
   bool ok = true;
   for(int i = 0; ok && i < ArraySize(g_syms); i++) ok = SendTick(g_syms[i]);
   if(ok) ok = SendAccount();
   if(ok) ok = SendPositions();
   if(!ok) Disconnect();                          // reconnect on the next timer
  }
