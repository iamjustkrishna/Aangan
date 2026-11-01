/* @ds-bundle: {"format":4,"namespace":"FamilyVault","components":[{"name":"Button"},{"name":"Icon"},{"name":"Avatar"},{"name":"AvatarStack"},{"name":"UploadStatus"},{"name":"VideoCard"},{"name":"FilterChips"},{"name":"TopBar"},{"name":"BottomNav"},{"name":"AddSheet"},{"name":"RecordButton"},{"name":"DriveConnect"},{"name":"InviteCard"},{"name":"MemberRow"},{"name":"PhoneFrame"}]} */
(function () {
  var R = window.React, h = R.createElement;
  function cx() { return Array.prototype.filter.call(arguments, Boolean).join(' '); }
  function omit(p, keys) { var o = {}; for (var k in p) if (keys.indexOf(k) < 0) o[k] = p[k]; return o; }

  var ICONS = {
    home: '<path d="M3.5 11 12 3.5l8.5 7.5"/><path d="M5.5 9.5V20h13V9.5"/><path d="M10 20v-5h4v5"/>',
    'home-fill': '<path class="f" d="M12 2.8 2.8 11a1 1 0 0 0 1.3 1.5l.4-.3V20a1 1 0 0 0 1 1H10v-6h4v6h4.5a1 1 0 0 0 1-1v-7.8l.4.3A1 1 0 0 0 21.2 11z"/>',
    users: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c.8-3.6 3.4-5.5 6.5-5.5s5.7 1.9 6.5 5.5"/><circle cx="17" cy="9" r="2.5"/><path d="M17 14.5c2.3 0 3.9 1.5 4.5 4.5"/>',
    upload: '<path d="M12 15.5V4"/><path d="m7 9 5-5 5 5"/><path d="M4 15.5V19a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3.5"/>',
    play: '<path class="f" d="M8 5.2v13.6a.8.8 0 0 0 1.2.7l10.6-6.8a.8.8 0 0 0 0-1.4L9.2 4.5A.8.8 0 0 0 8 5.2z"/>',
    'cloud-check': '<path d="M7 19h10a4.5 4.5 0 0 0 .7-8.95A6 6 0 0 0 6.1 9.2 5 5 0 0 0 7 19z"/><path d="m9.5 14 2 2 3.5-3.5"/>',
    'wifi-off': '<path d="m3 3 18 18"/><path d="M8.8 16.6a4.6 4.6 0 0 1 6.4 0"/><path d="M5.2 12.9a9.6 9.6 0 0 1 4.3-2.4"/><path d="M14.6 10.6a9.6 9.6 0 0 1 4.2 2.3"/><path d="M2 9.2a14.5 14.5 0 0 1 4-2.6"/><path d="M11 5.6A14.5 14.5 0 0 1 22 9.2"/><path d="M12 20h.01"/>',
    alert: '<circle cx="12" cy="12" r="9"/><path d="M12 7.5V13"/><path d="M12 16.5h.01"/>',
    check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
    plus: '<path d="M12 5v14"/><path d="M5 12h14"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.5 2"/>',
    mail: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3.5 6.5 8.5 6.5 8.5-6.5"/>',
    folder: '<path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
    lock: '<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
    retry: '<path d="M20 11a8 8 0 0 0-14.3-4.9L4 8"/><path d="M4 3.5V8h4.5"/><path d="M4 13a8 8 0 0 0 14.3 4.9L20 16"/><path d="M20 20.5V16h-4.5"/>',
    camera: '<path d="M4 8h3l2-3h6l2 3h3v11H4z"/><circle cx="12" cy="13" r="3.5"/>',
    image: '<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="9.5" r="1.8"/><path d="m21 16-5-5-9 9"/>',
    search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>',
    more: '<circle class="f" cx="12" cy="5" r="1.8"/><circle class="f" cx="12" cy="12" r="1.8"/><circle class="f" cx="12" cy="19" r="1.8"/>',
    user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c1-4.4 4.2-6.5 8-6.5s7 2.1 8 6.5"/>',
    flip: '<path d="M4 8h3l2-3h6l2 3h3v11H4z"/><path d="M9 13.5a3 3 0 0 1 5.2-2"/><path d="M15 13.5a3 3 0 0 1-5.2 2"/>',
    chevron: '<path d="m9 6 6 6-6 6"/>',
    back: '<path d="M15 6l-6 6 6 6"/>',
    x: '<path d="M6 6l12 12"/><path d="M18 6 6 18"/>'
  };

  function Icon(p) {
    var s = p.size || 24;
    return h('svg', {
      className: cx('fv-icon', p.className), width: s, height: s, viewBox: '0 0 24 24',
      role: p.label ? 'img' : undefined, 'aria-label': p.label, 'aria-hidden': p.label ? undefined : 'true',
      dangerouslySetInnerHTML: { __html: ICONS[p.name] || '' }
    });
  }

  function Button(p) {
    var v = p.variant || 'tonal', sz = p.size || 'lg';
    var rest = omit(p, ['variant', 'size', 'icon', 'full', 'className', 'children']);
    return h('button', Object.assign({ type: 'button' }, rest, {
      className: cx('fv-btn', 'fv-btn-' + v, 'fv-btn-' + sz, p.full && 'fv-btn-full', p.className)
    }), p.icon && h(Icon, { name: p.icon, size: 20 }), h('span', null, p.children));
  }

  function IconButton(p) {
    return h('button', { type: 'button', className: cx('fv-iconbtn', p.className), 'aria-label': p.label, onClick: p.onClick }, h(Icon, { name: p.icon }));
  }

  function initials(n) {
    var parts = String(n || '?').trim().split(/\s+/);
    return (parts[0].charAt(0) + (parts[1] ? parts[1].charAt(0) : '')).toUpperCase();
  }
  function toneOf(n) { var s = 0; n = String(n || ''); for (var i = 0; i < n.length; i++) s = (s + n.charCodeAt(i)) % 6; return s + 1; }

  function Avatar(p) {
    var t = p.tone || toneOf(p.name);
    return h('span', {
      className: cx('fv-avatar', 'fv-avatar-' + (p.size || 'md'), 'fv-tone-' + t, p.ring && 'fv-avatar-ring', p.className),
      role: 'img', 'aria-label': p.name, title: p.name
    }, initials(p.name));
  }

  function AvatarStack(p) {
    var max = p.max || 4, people = p.people || [], shown = people.slice(0, max), extra = people.length - shown.length, sz = p.size || 'sm';
    return h('span', { className: 'fv-stack', 'aria-label': people.length + ' people' },
      shown.map(function (m, i) { return h(Avatar, { key: i, name: m.name || m, tone: m.tone, size: sz, ring: true }); }),
      extra > 0 && h('span', { className: cx('fv-avatar', 'fv-avatar-' + sz, 'fv-avatar-ring', 'fv-avatar-more') }, '+' + extra));
  }

  var STATUS = {
    queued: { icon: 'clock', text: function () { return 'Queued'; } },
    uploading: { icon: 'upload', text: function (p) { return 'Uploading ' + Math.round(p || 0) + '%'; } },
    waiting: { icon: 'wifi-off', text: function () { return 'Waiting for internet'; } },
    saved: { icon: 'check', text: function () { return 'Saved'; } },
    failed: { icon: 'alert', text: function () { return 'Upload failed'; } }
  };

  function UploadStatus(p) {
    var st = STATUS[p.state] || STATUS.queued;
    return h('span', { className: cx('fv-status', 'fv-status-' + (p.state || 'queued'), p.compact && 'fv-status-compact'), role: 'status' },
      h(Icon, { name: st.icon, size: p.compact ? 14 : 16 }), st.text(p.progress));
  }

  function Progress(p) {
    return h('div', { className: cx('fv-progress', p.warn && 'fv-progress-warn', p.className), role: 'progressbar', 'aria-valuemin': 0, 'aria-valuemax': 100, 'aria-valuenow': Math.round(p.value || 0) },
      h('i', { style: { width: Math.max(0, Math.min(100, p.value || 0)) + '%' } }));
  }

  function Thumb(p) {
    return h('div', { className: cx('fv-thumb', 'fv-thumb-' + (p.tone || 1), p.bleed && 'fv-thumb-bleed') },
      p.duration && h('span', { className: 'fv-duration' }, p.duration),
      p.status && p.status !== 'saved' && h('span', { className: 'fv-thumb-status' }, h(UploadStatus, { state: p.status, progress: p.progress, compact: true })),
      p.status === 'uploading' && h(Progress, { value: p.progress, className: 'fv-thumb-progress' }));
  }

  function VideoCard(p) {
    var compact = p.layout === 'compact';
    return h('article', { className: cx('fv-video', compact && 'fv-video-compact'), tabIndex: 0, onClick: p.onOpen },
      h(Thumb, { tone: p.tone, duration: p.duration, status: p.status, progress: p.progress, bleed: p.bleed && !compact }),
      h('div', { className: 'fv-video-meta' },
        !compact && h(Avatar, { name: p.avatarName || p.uploader, tone: p.uploaderTone, size: 'md' }),
        h('div', { className: 'fv-video-text' },
          h('h3', { className: 'fv-video-title' }, p.title),
          h('p', { className: 'fv-video-sub' }, [p.uploader, p.when].filter(Boolean).join(' · '))),
        !compact && h('button', { type: 'button', className: 'fv-more', 'aria-label': 'More options', onClick: function (e) { e.stopPropagation(); } }, h(Icon, { name: 'more', size: 20 }))));
  }

  function FilterChips(p) {
    var items = p.items || [], sel = p.selected || items[0];
    return h('div', { className: 'fv-chips', role: 'tablist' }, items.map(function (it) {
      var on = it === sel;
      return h('button', { key: it, type: 'button', role: 'tab', 'aria-selected': on, className: cx('fv-chip', on && 'is-on'), onClick: p.onSelect ? function () { p.onSelect(it); } : undefined }, it);
    }));
  }

  function TopBar(p) {
    return h('header', { className: cx('fv-topbar', p.back && 'fv-topbar-back') },
      p.back && h(IconButton, { icon: 'back', label: 'Back', onClick: p.onBack }),
      h('div', { className: 'fv-topbar-text' },
        h('h1', { className: 'fv-topbar-title' }, p.title),
        p.subtitle && h('p', { className: 'fv-topbar-sub' }, p.subtitle)),
      p.search && h(IconButton, { icon: 'search', label: 'Search videos' }),
      p.me && h('button', { type: 'button', className: 'fv-iconbtn', 'aria-label': 'Your profile' }, h(Avatar, { name: p.me.name || p.me, tone: p.me.tone, size: 'sm' })),
      p.action);
  }

  var TABS = [
    { id: 'home', label: 'Home', icon: 'home', on: 'home-fill' },
    { id: 'members', label: 'Family', icon: 'users' },
    { id: 'add', label: 'Add', icon: 'plus' },
    { id: 'uploads', label: 'Uploads', icon: 'upload' },
    { id: 'you', label: 'You', icon: 'user' }
  ];

  function BottomNav(p) {
    var active = p.active || 'home';
    return h('nav', { className: 'fv-nav', 'aria-label': 'Main' }, TABS.map(function (t) {
      var on = t.id === active, add = t.id === 'add';
      return h('button', {
        key: t.id, type: 'button', className: cx('fv-tab', on && 'is-active', add && 'fv-tab-add'),
        'aria-current': on ? 'page' : undefined, 'aria-label': add ? 'Add a video' : undefined,
        onClick: p.onChange ? function () { p.onChange(t.id); } : undefined
      },
        h('span', { className: 'fv-tab-icon' }, h(Icon, { name: on && t.on ? t.on : t.icon, size: add ? 28 : 24 })),
        !add && h('span', { className: 'fv-tab-label' }, t.label),
        t.id === 'uploads' && p.uploads > 0 && h('span', { className: 'fv-tab-badge' }, p.uploads));
    }));
  }

  function SheetRow(p) {
    return h('button', { type: 'button', className: 'fv-sheet-row', onClick: p.onClick },
      h('span', { className: 'fv-sheet-icon' }, h(Icon, { name: p.icon })),
      h('span', { className: 'fv-grow' }, h('span', { className: 'fv-sheet-label' }, p.label), h('span', { className: 'fv-sheet-hint' }, p.hint)));
  }

  function AddSheet(p) {
    return h('div', { className: 'fv-sheet', role: 'dialog', 'aria-label': 'Add a video' },
      h('span', { className: 'fv-sheet-handle' }),
      h('h2', { className: 'fv-sheet-title' }, 'Add a video'),
      h(SheetRow, { icon: 'camera', label: 'Record a video', hint: 'Opens the camera', onClick: p.onRecord }),
      h(SheetRow, { icon: 'image', label: 'Choose from gallery', hint: 'Pick videos already on your phone', onClick: p.onGallery }),
      h(Button, { variant: 'quiet', full: true, onClick: p.onCancel }, 'Cancel'));
  }

  function fmt(s) { s = Math.max(0, s | 0); return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0'); }

  function RecordButton(p) {
    var rec = !!p.recording;
    return h('div', { className: 'fv-record-wrap' },
      h('button', { type: 'button', className: cx('fv-record', rec && 'is-recording'), 'aria-label': rec ? 'Stop recording' : 'Start recording', 'aria-pressed': rec, onClick: p.onPress },
        h('span', { className: 'fv-record-core' })),
      p.showLabel !== false && h('span', { className: cx('fv-record-label', rec && 'is-live') },
        rec ? [h('span', { key: 'd', className: 'fv-live-dot' }), fmt(p.seconds)] : 'Tap to record'));
  }

  function DriveConnect(p) {
    var state = p.state || 'idle';
    if (state === 'connected') {
      var used = p.usedGB || 0, total = p.totalGB || 15, pct = (used / total) * 100;
      return h('section', { className: 'fv-card' },
        h('div', { className: 'fv-line' },
          h('span', { className: 'fv-glyph' }, h(Icon, { name: 'folder' })),
          h('div', { className: 'fv-grow' },
            h('h3', { className: 'fv-card-title' }, 'Google Drive'),
            h('p', { className: 'fv-card-sub' }, p.email)),
          h(UploadStatus, { state: 'saved', compact: true })),
        h('div', { className: 'fv-meter' },
          h(Progress, { value: pct, warn: pct >= 85 }),
          h('p', { className: 'fv-card-sub fv-tnum' }, used.toFixed(1) + ' GB of ' + total + ' GB used')),
        pct >= 85 && h('p', { className: 'fv-note' }, 'Your Drive is almost full. New videos will wait until there is space.'));
    }
    var busy = state === 'connecting';
    return h('section', { className: 'fv-connect' },
      h('ul', { className: 'fv-points' },
        h('li', null, h('span', { className: 'fv-point-icon' }, h(Icon, { name: 'folder', size: 20 })), h('span', null, 'Videos are saved in your own Google Drive')),
        h('li', null, h('span', { className: 'fv-point-icon' }, h(Icon, { name: 'lock', size: 20 })), h('span', null, 'The app can only see videos it saves there')),
        h('li', null, h('span', { className: 'fv-point-icon' }, h(Icon, { name: 'users', size: 20 })), h('span', null, 'Only your family can watch them'))),
      h(Button, { variant: 'primary', full: true, disabled: busy, onClick: p.onConnect }, busy ? 'Waiting for Google…' : 'Connect Google Drive'));
  }

  function InviteCard(p) {
    if (p.direction === 'outgoing') {
      return h('div', { className: 'fv-member' },
        h(Avatar, { name: p.to, tone: p.tone, size: 'md' }),
        h('div', { className: 'fv-member-text' },
          h('p', { className: 'fv-member-name' }, p.to),
          h('p', { className: 'fv-member-rel' }, 'Invited ' + (p.when || '') + ' · ' + (p.contact || ''))),
        h(Button, { variant: 'tonal', size: 'md', onClick: p.onResend }, 'Remind'));
    }
    return h('section', { className: 'fv-card fv-invite' },
      h(AvatarStack, { people: p.members || [], max: 4, size: 'md' }),
      h('p', { className: 'fv-invite-text' }, h('strong', null, p.from), ' invited you to ', h('strong', null, p.family)),
      h('p', { className: 'fv-card-sub' }, [(p.members ? p.members.length + ' members' : null), (p.videos != null ? p.videos + ' videos' : null)].filter(Boolean).join(' · ')),
      h('div', { className: 'fv-stackbtns' },
        h(Button, { variant: 'primary', full: true, onClick: p.onAccept }, 'Join family'),
        h(Button, { variant: 'quiet', full: true, onClick: p.onDecline }, 'Not now')));
  }

  function MemberRow(p) {
    return h('div', { className: 'fv-member' },
      h(Avatar, { name: p.name, tone: p.tone, size: 'md' }),
      h('div', { className: 'fv-member-text' },
        h('p', { className: 'fv-member-name' }, p.name, p.you && h('span', { className: 'fv-you' }, ' (you)')),
        p.relation && h('p', { className: 'fv-member-rel' }, p.relation)),
      p.role === 'admin' && h('span', { className: 'fv-badge' }, 'Admin'),
      p.action);
  }

  function PhoneFrame(p) {
    return h('div', { className: cx('fv-phone', p.dark && 'fv-phone-dark'), style: p.height ? { height: p.height + 'px' } : undefined },
      h('div', { className: 'fv-phone-status' }, h('span', null, p.time || '9:41'), h('span', null, '5G  ▮▮▮')),
      h('div', { className: 'fv-phone-body' }, p.children));
  }

  var C = window;
  C.FamilyVault = C.FamilyVault || {};
  Object.assign(C.FamilyVault, {
    Button: Button, IconButton: IconButton, Icon: Icon, Avatar: Avatar, AvatarStack: AvatarStack, UploadStatus: UploadStatus, Progress: Progress,
    VideoCard: VideoCard, FilterChips: FilterChips, TopBar: TopBar, BottomNav: BottomNav, AddSheet: AddSheet, RecordButton: RecordButton,
    DriveConnect: DriveConnect, InviteCard: InviteCard, MemberRow: MemberRow, PhoneFrame: PhoneFrame
  });
})();
