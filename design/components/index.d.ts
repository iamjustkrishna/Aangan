import * as React from 'react';

export type IconName = 'home' | 'home-fill' | 'users' | 'upload' | 'play' | 'cloud-check' | 'wifi-off' | 'alert' | 'check' | 'plus' | 'clock' | 'mail' | 'folder' | 'lock' | 'retry' | 'camera' | 'image' | 'search' | 'more' | 'user' | 'flip' | 'chevron' | 'back' | 'x';
export type Tone = 1 | 2 | 3 | 4 | 5 | 6;
export type UploadState = 'queued' | 'uploading' | 'waiting' | 'saved' | 'failed';
export type Person = string | { name: string; tone?: Tone };

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  /** primary = solid blue, once per screen. Default tonal. */
  variant?: 'primary' | 'tonal' | 'outline' | 'quiet' | 'danger';
  /** lg = 56px (default). md = 40px, only inside list rows. */
  size?: 'lg' | 'md';
  icon?: IconName;
  full?: boolean;
  children: React.ReactNode;
}
export declare function Button(props: ButtonProps): JSX.Element;

export interface IconButtonProps { icon: IconName; label: string; onClick?: () => void; className?: string; }
export declare function IconButton(props: IconButtonProps): JSX.Element;

export interface IconProps { name: IconName; size?: number; label?: string; className?: string; }
export declare function Icon(props: IconProps): JSX.Element;

export interface AvatarProps { name: string; tone?: Tone; size?: 'sm' | 'md' | 'lg'; ring?: boolean; }
export declare function Avatar(props: AvatarProps): JSX.Element;

export interface AvatarStackProps { people: Person[]; max?: number; size?: 'sm' | 'md' | 'lg'; }
export declare function AvatarStack(props: AvatarStackProps): JSX.Element;

export interface UploadStatusProps { state: UploadState; progress?: number; compact?: boolean; }
export declare function UploadStatus(props: UploadStatusProps): JSX.Element;

export interface ProgressProps { value: number; warn?: boolean; className?: string; }
export declare function Progress(props: ProgressProps): JSX.Element;

export interface VideoCardProps {
  title: string;
  /** First name shown in the "who · when" line. */
  uploader: string;
  /** Full name for the avatar initials; defaults to uploader. */
  avatarName?: string;
  uploaderTone?: Tone;
  when: string;
  duration?: string;
  /** Placeholder colour 1–4 until Drive returns a thumbnail. */
  tone?: 1 | 2 | 3 | 4;
  status?: UploadState;
  progress?: number;
  /** Full-width thumbnail for the Home feed. */
  bleed?: boolean;
  layout?: 'feed' | 'compact';
  onOpen?: () => void;
}
export declare function VideoCard(props: VideoCardProps): JSX.Element;

export interface FilterChipsProps { items: string[]; selected?: string; onSelect?: (item: string) => void; }
export declare function FilterChips(props: FilterChipsProps): JSX.Element;

export interface TopBarProps { title: string; subtitle?: string; search?: boolean; me?: Person; back?: boolean; onBack?: () => void; action?: React.ReactNode; }
export declare function TopBar(props: TopBarProps): JSX.Element;

export interface BottomNavProps { active?: 'home' | 'members' | 'add' | 'uploads' | 'you'; uploads?: number; onChange?: (id: string) => void; }
export declare function BottomNav(props: BottomNavProps): JSX.Element;

export interface AddSheetProps { onRecord?: () => void; onGallery?: () => void; onCancel?: () => void; }
export declare function AddSheet(props: AddSheetProps): JSX.Element;

export interface RecordButtonProps { recording?: boolean; seconds?: number; showLabel?: boolean; onPress?: () => void; }
export declare function RecordButton(props: RecordButtonProps): JSX.Element;

export interface DriveConnectProps { state?: 'idle' | 'connecting' | 'connected'; email?: string; usedGB?: number; totalGB?: number; onConnect?: () => void; }
export declare function DriveConnect(props: DriveConnectProps): JSX.Element;

export interface InviteCardProps {
  direction?: 'incoming' | 'outgoing';
  from?: string; family?: string; members?: Person[]; videos?: number; onAccept?: () => void; onDecline?: () => void;
  to?: string; tone?: Tone; contact?: string; when?: string; onResend?: () => void;
}
export declare function InviteCard(props: InviteCardProps): JSX.Element;

export interface MemberRowProps { name: string; relation?: string; tone?: Tone; role?: 'admin' | 'member'; you?: boolean; action?: React.ReactNode; }
export declare function MemberRow(props: MemberRowProps): JSX.Element;

export interface PhoneFrameProps { time?: string; height?: number; dark?: boolean; children: React.ReactNode; }
/** Mockup frame for screen previews only. */
export declare function PhoneFrame(props: PhoneFrameProps): JSX.Element;
