import org.eclipse.jface.text.ITextInputListener;
		for (int lineNo = 0; lineNo < nrOfLines && !monitor.isCanceled();) {
				for (int i = 0; i < 200 && lineNo < nrOfLines; i++, lineNo++) {
						IRegion lineInformation = document.getLineInformation(lineNo);
						Color lineColor = getDiffLineColor(document.get(offset, length));

						if (lineColor != null) {
							diffTextViewer.setTextColor(lineColor, offset, length, true);
						}
			} finally {
			while (display.readAndDispatch()) {
			return null;
			return null;
	class UpdateDiffViewerJob extends UIJob implements ITextInputListener {
		private IProgressMonitor monitor;
		public IStatus runInUIThread(IProgressMonitor progressMonitor) {

			this.monitor = progressMonitor;
			try {
				diffTextViewer.addTextInputListener(this);
				applyLineColoringToDiffViewer(monitor);
			} finally {
				diffTextViewer.removeTextInputListener(this);
				this.monitor = null;
			}
			return progressMonitor.isCanceled()? Status.CANCEL_STATUS : Status.OK_STATUS;

		/**
		 * @see org.eclipse.jface.text.ITextInputListener#inputDocumentAboutToBeChanged(org.eclipse.jface.text.IDocument, org.eclipse.jface.text.IDocument)
		 */
		public void inputDocumentAboutToBeChanged(IDocument oldInput, IDocument newInput) {
			monitor.setCanceled(true);
		}

		/**
		 * @see org.eclipse.jface.text.ITextInputListener#inputDocumentChanged(org.eclipse.jface.text.IDocument, org.eclipse.jface.text.IDocument)
		 */
		public void inputDocumentChanged(IDocument oldInput, IDocument newInput) {
		}