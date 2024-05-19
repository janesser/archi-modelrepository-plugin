/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package org.archicontribs.modelrepository.views.history;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.archicontribs.modelrepository.IModelRepositoryImages;
import org.archicontribs.modelrepository.grafico.BranchInfo;
import org.archicontribs.modelrepository.grafico.BranchStatus;
import org.archicontribs.modelrepository.grafico.GraficoFileConventions;
import org.archicontribs.modelrepository.grafico.IArchiRepository;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ILazyContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import com.archimatetool.editor.ui.UIUtils;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IFolder;


/**
 * History Table Viewer
 */
public class HistoryTableViewer extends TableViewer {
    
    private RevCommit fLocalCommit, fOriginCommit;
    
    private BranchInfo fSelectedBranch;
    
    /**
     * Constructor
     */
    public HistoryTableViewer(Composite parent) {
        super(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.FULL_SELECTION | SWT.VIRTUAL);
        
        // Mac Item height
        UIUtils.fixMacSiliconItemHeight(getTable());
        
        setup(parent);
        
        setContentProvider(new HistoryContentProvider());
        setLabelProvider(new HistoryLabelProvider());
        
        ColumnViewerToolTipSupport.enableFor(this);
        
        setUseHashlookup(true);
    }

    /**
     * Set things up.
     */
    protected void setup(Composite parent) {
        getTable().setHeaderVisible(true);
        getTable().setLinesVisible(false);
        
        TableColumnLayout tableLayout = (TableColumnLayout)parent.getLayout();
        
        TableViewerColumn column = new TableViewerColumn(this, SWT.NONE, 0);
        column.getColumn().setText(Messages.HistoryTableViewer_0);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(10, false));
        
        column = new TableViewerColumn(this, SWT.NONE, 1);
        column.getColumn().setText(Messages.HistoryTableViewer_1);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(50, false));

        column = new TableViewerColumn(this, SWT.NONE, 2);
        column.getColumn().setText(Messages.HistoryTableViewer_2);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(20, false));
    
        column = new TableViewerColumn(this, SWT.NONE, 3);
        column.getColumn().setText(Messages.HistoryTableViewer_3);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(20, false));
    }
    
    public void doSetInput(IArchiRepository archiRepo) {
        // Get BranchStatus and currentLocalBranch
        try {
            BranchStatus branchStatus = archiRepo.getBranchStatus();
            if(branchStatus != null) {
                fSelectedBranch = branchStatus.getCurrentLocalBranch();
            }
        }
        catch(IOException | GitAPIException ex) {
            ex.printStackTrace();
        }
        
        setInput(archiRepo);
        
        // avoid bogus horizontal scrollbar cheese
        Display.getCurrent().asyncExec(() -> {
            if(!getTable().isDisposed()) {
                getTable().getParent().layout();
            }
        });

        // Select first row
        //Object element = getElementAt(0);
        //if(element != null) {
        //    setSelection(new StructuredSelection(element), true);
        //}
    }
	    
    private static record HistoryInput(IArchiRepository repo, IArchimateModelObject obj) {} 
    
	public void doSetInput(IArchiRepository selectedRepository, Object selected) {
		if (selected instanceof IArchimateModelObject)
			setInput(new HistoryInput(selectedRepository, (IArchimateModelObject)selected));
		else
			doSetInput(selectedRepository);
	}
    
    public void setSelectedBranch(BranchInfo branchInfo) {
        if(branchInfo != null && branchInfo.equals(fSelectedBranch)) {
            return;
        }

        fSelectedBranch = branchInfo;
        
        setInput(getInput());
    }
    
    // ===============================================================================================
	// ===================================== Table Model ==============================================
	// ===============================================================================================
    
    /**
     * The Model for the Table.
     */
    class HistoryContentProvider implements ILazyContentProvider {
        List<RevCommit> commits;
        
        @Override
        public void inputChanged(Viewer v, Object oldInput, Object newInput) {
            commits = getCommits(newInput);
            setItemCount(commits.size());
        }

        @Override
        public void dispose() {
        }
        
        List<RevCommit> getCommits(Object parent) {
            List<RevCommit> commits = new ArrayList<RevCommit>();
            fLocalCommit = null;
            fOriginCommit = null;
            
            IArchiRepository repo = null;
            File elemFile = null;
            
            if (parent instanceof HistoryInput && ((HistoryInput) parent).obj().eContainer() instanceof IFolder) {
            	HistoryInput input = (HistoryInput) parent;
            	repo = input.repo();
            	IFolder folder = (IFolder) input.obj().eContainer();
            	elemFile = GraficoFileConventions.forElement(
            			new File(repo.getLocalRepositoryFolder(), "model"), 
            			folder, 
            			input.obj());
            } else if (parent instanceof IArchiRepository)
            	repo = (IArchiRepository) parent;
            
            if(fSelectedBranch == null) {
                return commits;
            }
            
            // Local Repo was deleted
            if(repo == null || !repo.getLocalRepositoryFolder().exists()) {
                return commits;
            }

            try(Repository repository = Git.open(repo.getLocalRepositoryFolder()).getRepository()) {
                // a RevWalk allows to walk over commits based on some filtering that is defined
                try(RevWalk revWalk = new RevWalk(repository)) {
					if (elemFile != null) {
						String elemPath = Path.of(repo.getLocalRepositoryFolder().getAbsolutePath())
								.relativize(
										Path.of(elemFile.getAbsolutePath())
								).toString();
						revWalk.setTreeFilter(PathFilter.create(elemPath));
					}
					
                    // Find the local branch
                    ObjectId objectID = repository.resolve(fSelectedBranch.getLocalBranchNameFor());
                    if(objectID != null) {
                        fLocalCommit = revWalk.parseCommit(objectID);
                        revWalk.markStart(fLocalCommit); 
                    }
                                        
                    // Find the remote branch
                    objectID = repository.resolve(fSelectedBranch.getRemoteBranchNameFor());
                    if(objectID != null) {
                        fOriginCommit = revWalk.parseCommit(objectID);
                        revWalk.markStart(fOriginCommit);
                    }
                    
                    // Collect the commits
                    for(RevCommit commit : revWalk ) {
                        commits.add(commit);
                    }
                    
                    revWalk.dispose();
                }
            }
            catch(IOException ex) {
                ex.printStackTrace();
            }
            
            return commits;
        }

        @Override
        public void updateElement(int index) {
            if(commits != null) {
                replace(commits.get(index), index);
            }
        }
    }
    
    // ===============================================================================================
	// ===================================== Label Model ==============================================
	// ===============================================================================================

    class HistoryLabelProvider extends CellLabelProvider {
        
        DateFormat dateFormat = DateFormat.getDateTimeInstance();
        
        public String getColumnText(RevCommit commit, int columnIndex) {
            switch(columnIndex) {
                case 0:
                    return commit.getName().substring(0, 8);
                    
                case 1:
                    return commit.getShortMessage();
                    
                case 2:
                    return commit.getAuthorIdent().getName();
                
                case 3:
                    return dateFormat.format(new Date(commit.getCommitTime() * 1000L));
                    
                default:
                    return null;
            }
        }

        @Override
        public void update(ViewerCell cell) {
            if(cell.getElement() instanceof RevCommit) {
                RevCommit commit = (RevCommit)cell.getElement();
                
                cell.setText(getColumnText(commit, cell.getColumnIndex()));
                
                if(cell.getColumnIndex() == 1) {
                    Image image = null;
                    
                    if(commit.equals(fLocalCommit) && commit.equals(fOriginCommit)) {
                        image = IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_HISTORY_VIEW);
                    }
                    else if(commit.equals(fOriginCommit)) {
                        image = IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_REMOTE);
                    }
                    else if(commit.equals(fLocalCommit)) {
                        image = IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_LOCAL);
                    }
                    
                    cell.setImage(image);
                }
            }
        }
        
        @Override
        public String getToolTipText(Object element) {
            if(element instanceof RevCommit) {
                RevCommit commit = (RevCommit)element;
                
                String s = ""; //$NON-NLS-1$
                
                if(commit.equals(fLocalCommit) && commit.equals(fOriginCommit)) {
                    s += Messages.HistoryTableViewer_4 + " "; //$NON-NLS-1$
                }
                else if(commit.equals(fLocalCommit)) {
                    s += Messages.HistoryTableViewer_5 + " "; //$NON-NLS-1$
                }

                else if(commit.equals(fOriginCommit)) {
                    s += Messages.HistoryTableViewer_6 + " "; //$NON-NLS-1$
                }
                
                s += commit.getFullMessage().trim();
                
                return s;
            }
            
            return null;
        }
    }
}
