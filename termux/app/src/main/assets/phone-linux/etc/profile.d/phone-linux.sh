# phone-linux: run the first-time setup once, in the first interactive session.
case $- in
*i*)
	if [ ! -e "$HOME/.phone-linux/setup-done" ] && [ -z "${PHONE_LINUX_SETUP_RUNNING:-}" ]; then
		PHONE_LINUX_SETUP_RUNNING=1 phone-linux-setup || echo "セットアップに失敗しました。phone-linux-setup で再実行できます。"
	fi
	;;
esac
