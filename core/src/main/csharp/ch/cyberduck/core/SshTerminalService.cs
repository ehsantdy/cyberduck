﻿//
// Copyright (c) 2010-2016 Yves Langisch. All rights reserved.
// http://cyberduck.io/
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// Bug fixes, suggestions and comments should be sent to:
// feedback@cyberduck.io
//

using System;
using System.Diagnostics;
using System.IO;
using System.Threading;
using System.Windows.Forms;
using ch.cyberduck.core;
using ch.cyberduck.core.local;
using ch.cyberduck.core.preferences;
using Application = ch.cyberduck.core.local.Application;
using Path = System.IO.Path;

namespace Ch.Cyberduck.Core
{
    public class SshTerminalService : TerminalService
    {
        public void open(Host host, ch.cyberduck.core.Path workdir)
        {
            if (Utils.IsWin101809)
            {
                if (TryStartBuiltinOpenSSH(host, workdir.getAbsolute()))
                {
                    return;
                }
            }
            if (Utils.IsWin10FallCreatorsUpdate)
            {
                if (TryStartBashSSH(host, workdir.getAbsolute()))
                {
                    return;
                }
            }

            TryStartPuTTy(host, workdir.getAbsolute());
        }

        private static string GetSystemPath()
        {
            var system = Environment.GetFolderPath(Environment.SpecialFolder.System);
            if (Environment.Is64BitOperatingSystem)
            {
                system = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Windows), "SysNative");
            }
            return system;
        }

        private bool TryStartBashSSH(Host host, string workdir)
        {
            var system = GetSystemPath();
            var bash = Path.Combine(system, "bash.exe");
            if (!File.Exists(bash))
            {
                return false;
            }

            var credentials = host.getCredentials();
            if (credentials.isPublicKeyAuthentication())
            {
                // there is no way of determining the mount path for each
                // and every distribution. Assume no public key authentication.
                return false;
            }

            var hostname = host.getHostname();
            var username = credentials.getUsername();
            var port = host.getPort();

            Process process = new Process();
            process.StartInfo.FileName = bash;
            process.StartInfo.Arguments = $"-c \"{$"ssh -T -P {port} {username}@{hostname} 'cd \\\"{workdir}\\\"'"}\"";

            var success = process.Start();
            if (success)
            {
                if (!process.WaitForExit(500))
                {
                    return true;
                }
                return process.ExitCode == 0;
            }
            return false;
        }

        private bool TryStartBuiltinOpenSSH(Host host, string workdir)
        {
            var system = GetSystemPath();
            var openSSH = new DirectoryInfo(Path.Combine(system, "OpenSSH"));
            if (!openSSH.Exists)
            {
                return false;
            }
            var ssh = new FileInfo(Path.Combine(openSSH.FullName, "ssh.exe"));
            if (!ssh.Exists)
            {
                return false;
            }

            var credentials = host.getCredentials();
            var identity = credentials.isPublicKeyAuthentication();
            var args = identity ? string.Format("-i \"{0}\"", credentials.getIdentity().getAbsolute()) : "";
            var hostname = host.getHostname();
            var username = credentials.getUsername();
            var port = host.getPort();

            Process process = new Process();
            process.StartInfo.FileName = ssh.FullName;
            process.StartInfo.Arguments = $"{args} {username}@{hostname} -T -P {port} \"cd \\\"{workdir}\\\";\\$SHELL\"";
            return process.Start();
        }

        private bool TryStartPuTTy(Host host, string workdir)
        {
            if (!File.Exists(PreferencesFactory.get().getProperty("terminal.command.ssh")))
            {
                OpenFileDialog selectDialog = new OpenFileDialog();
                selectDialog.Filter = "PuTTY executable (.exe)|*.exe";
                selectDialog.FilterIndex = 1;
                DialogResult result = DialogResult.None;
                Thread thread = new Thread(() => result = selectDialog.ShowDialog());
                thread.SetApartmentState(ApartmentState.STA);
                thread.Start();
                thread.Join();
                if (result == DialogResult.OK)
                {
                    PreferencesFactory.get().setProperty("terminal.command.ssh", selectDialog.FileName);
                }
                else
                {
                    return false;
                }
            }
            string tempFile = Path.GetTempFileName();
            bool identity = host.getCredentials().isPublicKeyAuthentication();
            TextWriter tw = new StreamWriter(tempFile);
            tw.WriteLine("cd {0} && exec $SHELL", workdir);
            tw.Close();
            String ssh = String.Format(PreferencesFactory.get().getProperty("terminal.command.ssh.args"),
                identity
                    ? string.Format("-i \"{0}\"", host.getCredentials().getIdentity().getAbsolute())
                    : String.Empty, host.getCredentials().getUsername(), host.getHostname(),
                Convert.ToString(host.getPort()), tempFile);
            return ApplicationLauncherFactory.get()
                .open(
                    new Application(PreferencesFactory.get().getProperty("terminal.command.ssh"), null), ssh);
        }
    }
}
