# Workspace terminal

OrbitForge currently provides a limited Kotlin-based workspace command runner. It does not start `/system/bin/sh` and does not execute arbitrary shell syntax. Supported commands are `help`, `pwd`, `ls`, `find`, `cat`, `head`, `grep`, `wc`, `mkdir`, `touch`, `echo`, and `clear`.

Every filesystem path is resolved inside the app-private workspace. Shell operators such as pipes, redirection, command chaining, and command substitution are rejected. This is a safe starter terminal, not a full Linux environment. A future runtime adapter can connect an explicitly configured Termux or remote executor with separate permissions.
