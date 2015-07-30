/*******************************************************************************
 * Copyright (c) 2006, 2015 THALES GLOBAL SERVICES.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *  
 * Contributors:
 *    Thales - initial API and implementation
 *******************************************************************************/

package org.polarsys.capella.core.platform.sirius.ui.session;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.emf.common.ui.dialogs.DiagnosticDialog;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.FeatureNotFoundException;
import org.eclipse.emf.ecore.xmi.PackageNotFoundException;
import org.eclipse.emf.ecore.xmi.UnresolvedReferenceException;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.gmf.runtime.emf.core.resources.GMFResource;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.business.api.session.factory.SessionFactory;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.polarsys.capella.common.ef.command.AbstractReadWriteCommand;
import org.polarsys.capella.common.helpers.EcoreUtil2;
import org.polarsys.capella.common.helpers.TransactionHelper;
import org.polarsys.capella.common.libraries.ILibraryManager;
import org.polarsys.capella.common.libraries.IModel;
import org.polarsys.capella.common.libraries.IModelIdentifier;
import org.polarsys.capella.common.libraries.manager.LibraryManagerExt;
import org.polarsys.capella.common.mdsofa.common.constant.ICommonConstants;
import org.polarsys.capella.common.tools.report.EmbeddedMessage;
import org.polarsys.capella.common.tools.report.config.registry.ReportManagerRegistry;
import org.polarsys.capella.common.tools.report.util.IReportManagerDefaultComponents;
import org.polarsys.capella.core.platform.sirius.ui.actions.CapellaActionsActivator;
import org.polarsys.capella.core.platform.sirius.ui.preferences.ICapellaPreferences;
import org.polarsys.capella.core.preferences.Activator;
import org.polarsys.kitalpha.emde.xmi.UnknownEObject;

/**
 * The Capella session helper.
 */
public class CapellaSessionHelper {
  /**
   * Capella semantic model NS URI prefix.
   */
  private static final String SEMANTIC_MODEL_NS_URI_PREFIX = "http://www.polarsys.org/capella/core"; //$NON-NLS-1$
  /**
   * Melody legacy semantic model NS URI prefix (still needed to migrate old models).
   */
  private static final String LEGACY_SEMANTIC_MODEL_NS_URI_PREFIX = "http://www.thalesgroup.com/mde/melody"; //$NON-NLS-1$

  /**
   * Log4j reference logger.
   */
  private static final Logger __logger = ReportManagerRegistry.getInstance().subscribe(IReportManagerDefaultComponents.MODEL);

  /**
   * @param session
   * @return
   */
  public static boolean checkModelsCompliancy(Session session) {
    ResourceSet resourceSet = session.getSessionResource().getResourceSet();
    return checkModelsCompliancy(resourceSet);
  }

  /**
   * @param resourceSet
   * @return
   */
  public static boolean checkModelsCompliancy(ResourceSet resourceSet) {
    Diagnostic diagnostic = checkModelCompliant(resourceSet);
    if (diagnostic.getSeverity() == Diagnostic.ERROR) {
      reportError(diagnostic);
      return false;
    }
    return true;
  }

  /**
   * Returns whether all referenced libraries from the given session are available
   * @param session
   * @return
   */
  public static IStatus checkLibrariesAvailability(Session session) {
    Collection<Resource> res = session.getSemanticResources();
    for (Resource resource : res) {
      IModelIdentifier modelId = ILibraryManager.INSTANCE.getModelIdentifier(resource.getURI());
      IModel model = ILibraryManager.INSTANCE.getModel(ILibraryManager.DEFAULT_EDITING_DOMAIN, modelId);
      Collection<IModelIdentifier> unavailable = LibraryManagerExt.getAllUnavailableReferences(model);
      if (!unavailable.isEmpty()) {
        StringBuffer buffer = new StringBuffer();
        for (IModelIdentifier identifier : unavailable) {
          buffer.append(identifier);
          buffer.append(ICommonConstants.COMMA_CHARACTER);
        }
        buffer.deleteCharAt(buffer.length() - 1);
        String message = NLS.bind(Messages.CapellaSessionHelper_MissingLibraries_Message, buffer);
        reportError(message);
        return new Status(IStatus.ERROR, CapellaActionsActivator.getDefault().getPluginId(), message, null);
      }
    }
    return Status.OK_STATUS; // at the end there is no error.
  }

  /**
   * Check compliance of the model having the given URI.<br>
   * The check stops on the first detected error.
   * @param uri
   * @return an {@link IStatus} with severity OK, if no errors are detected or with severity ERROR (with a message and an exception) else.
   */
  public static IStatus checkModelsCompliancy(URI uri) {
    boolean detectVersion = Activator.getDefault().getPreferenceStore().getBoolean(ICapellaPreferences.PREFERENCE_DETECTION_VERSION);
    if (detectVersion) {
      ResourceSet resourceSet = new ResourceSetImpl();
      resourceSet.getLoadOptions().put(GMFResource.OPTION_ABORT_ON_ERROR, Boolean.TRUE);
      resourceSet.getLoadOptions().put(XMLResource.OPTION_RECORD_UNKNOWN_FEATURE, Boolean.FALSE);
      try {
        // Load the file in a temporary resource and catch loading errors.
        resourceSet.getResource(uri, true);
        EcoreUtil.resolveAll(resourceSet);
      } catch (Exception exception) {
        String handleLoadingErrors = handleLoadingErrors(exception);
        if (handleLoadingErrors == null) {
          return Status.OK_STATUS; // at the end there is no error.
        }
        reportError(handleLoadingErrors);
        return new Status(IStatus.ERROR, CapellaActionsActivator.getDefault().getPluginId(), handleLoadingErrors, exception);
      } finally {
        // Make sure all loaded resources in the temporary resourceSet are unloaded & removed.
        cleanResourceSet(resourceSet);
      }
    }

    return Status.OK_STATUS;
  }

  /**
   * This method checks if a model can be fully loaded without errors/warnings.<br>
   * It is like call {@link CapellaSessionHelper#checkModelsCompliancy(URI)} and then a more complete version of
   * {@link CapellaSessionHelper#checkModelCompliant(ResourceSet)}.<br>
   * FIXME Used only for Capella Team for now, to be used by Capella ?
   * @param uri
   * @return an IStatus with severity OK, WARNING or ERROR.
   */
  public static IStatus checkModelsFullCompliancy(URI uri) {
    final String pluginId = CapellaActionsActivator.getDefault().getPluginId();
    ResourceSet tempResourceSet = new ResourceSetImpl();
    tempResourceSet.getLoadOptions().put(GMFResource.OPTION_ABORT_ON_ERROR, Boolean.TRUE);
    tempResourceSet.getLoadOptions().put(XMLResource.OPTION_RECORD_UNKNOWN_FEATURE, Boolean.FALSE);
    try {
      // Load the file in a temporary resource and catch loading errors.
      tempResourceSet.getResource(uri, true);
      // Resources have been loaded, check if there has been other problems.
      // Look for first error.
      for (Resource resource : tempResourceSet.getResources()) {
        for (Resource.Diagnostic diagnostic : resource.getErrors()) {
          Throwable exception = (diagnostic instanceof Throwable) ? (Throwable) diagnostic : null;
          return new Status(IStatus.ERROR, pluginId, diagnostic.getMessage(), exception);
        }
      }
      // Look for first warning.
      for (Resource resource : tempResourceSet.getResources()) {
        for (Resource.Diagnostic diagnostic : resource.getWarnings()) {
          Throwable exception = (diagnostic instanceof Throwable) ? (Throwable) diagnostic : null;
          return new Status(IStatus.WARNING, pluginId, diagnostic.getMessage(), exception);
        }
      }
    } catch (Exception exception) {
      String handleLoadingErrors = handleLoadingErrors(exception);
      if (handleLoadingErrors == null) {
        return Status.OK_STATUS; // at the end there is no error.
      }
      return new Status(IStatus.ERROR, pluginId, handleLoadingErrors, exception);
    } finally {
      // Make sure all loaded resources in the temporary resourceSet are unloaded & removed.
      cleanResourceSet(tempResourceSet);
    }

    return Status.OK_STATUS;
  }

  /**
   * Unload all resources on a temporary resourceSet, so resources are not removed
   * @param resourceSet the resource set to be cleaned
   */
  public static void cleanResourceSet(ResourceSet resourceSet) {
    if (null != resourceSet) {
      List<Resource> resources = new ArrayList<Resource>(resourceSet.getResources());
      for (Resource resource : resources) {
        resource.unload();
      }
    }
  }

  /**
   * Unload and remove resources on a session ResourceSet
   * @param session
   */
  public static void cleanResourceSet(Session session) {
    ResourceSet resourceSet = session.getSessionResource().getResourceSet();
    for (Resource resource : new ArrayList<Resource>(resourceSet.getResources())) {
      if (resource.getErrors().size() > 0) {
        resource.unload();
        resourceSet.getResources().remove(resource);
      }
    }
  }

  /**
   * @param resourceSet
   * @return
   */
  public static Diagnostic checkModelCompliant(ResourceSet resourceSet) {
    BasicDiagnostic diagnostics = new BasicDiagnostic("source", Diagnostic.OK, "Some extensions are missing", null); //$NON-NLS-1$ //$NON-NLS-2$
    for (Resource resource : resourceSet.getResources()) {
      if ("odesign".equals(resource.getURI().fileExtension())) { //$NON-NLS-1$
        for (Resource.Diagnostic diagnostic : resource.getErrors()) {
          if (diagnostic instanceof WrappedException) {
            Throwable th = ((WrappedException) diagnostic).getCause();
            diagnostics.add(BasicDiagnostic.toDiagnostic(th));
          }
        }
      } else if ("melodymodeller".equals(resource.getURI().fileExtension())) { //$NON-NLS-1$
        for (Resource.Diagnostic diagnostic : resource.getWarnings()) {
          if (diagnostic instanceof UnknownEObject) {
            UnknownEObject unknown = (UnknownEObject) diagnostic;
            String message = unknown.getMessage();
            BasicDiagnostic diag = new BasicDiagnostic(Diagnostic.ERROR, "source", 0, message, null); //$NON-NLS-1$
            diagnostics.add(diag);
          }
        }
      }
    }
    return diagnostics;
  }

  /**
   * @param message
   */
  private static void reportError(final String message) {
    final Display display = PlatformUI.getWorkbench().getDisplay();
    display.syncExec(new Runnable() {
      @Override
      public void run() {
        MessageDialog.openError(display.getActiveShell(), Messages.CapellaSessionHelper_SemanticModel_ErrorDialog_Title, message);
      }
    });
  }

  /**
   * @param diagnostic
   */
  private static void reportError(final Diagnostic diagnostic) {
    final Display display = PlatformUI.getWorkbench().getDisplay();
    display.syncExec(new Runnable() {
      @Override
      public void run() {
        DiagnosticDialog.openProblem(display.getActiveShell(), Messages.CapellaSessionHelper_SemanticModel_ErrorDialog_Title,
            "The session cannot be open.", diagnostic); //$NON-NLS-1$
      }
    });
  }

  /**
   * @param exception
   */
  public static void reportException(Exception exception) {
    String handleLoadingErrors = handleLoadingErrors(exception);
    if (handleLoadingErrors != null) {
      reportError(handleLoadingErrors);
    }
  }

  /**
   * @param exception
   * @return a not <code>null</code> error message.
   */
  public static String handleLoadingErrors(Exception exception) {
    String errorMsg = null;
    if (exception instanceof WrappedException) {
      Throwable cause = ((WrappedException) exception).getCause();
      if (cause instanceof FeatureNotFoundException) {
        // we can only get this exception during the migration process. In the usual case (ie no migration) we will get a PackageNotFoundException
        return null;
      } else if (cause instanceof PackageNotFoundException) {
        // Search for a package not found.
        PackageNotFoundException notFoundException = (PackageNotFoundException) cause;
        String packageNotFound = notFoundException.uri();
        // Find out if it is a Capella Package, if so, extract version number i.e last fragment.
        if (packageNotFound.startsWith(SEMANTIC_MODEL_NS_URI_PREFIX) || packageNotFound.startsWith(LEGACY_SEMANTIC_MODEL_NS_URI_PREFIX)) {
          String version = new Path(packageNotFound).lastSegment();
          errorMsg =
              Messages.CapellaSessionHelper_SemanticModel_Error_Message_WrongVersion_Part1 + version
                  + Messages.CapellaSessionHelper_SemanticModel_Error_Message_WrongVersion_Part2;
        } else {
          errorMsg = "A metamodel is missing: " + packageNotFound; //$NON-NLS-1$
        }
      } else if (cause instanceof org.eclipse.emf.ecore.xmi.ClassNotFoundException) {
        if (exception.getMessage().indexOf(".aird,") >= 0) { //$NON-NLS-1$
          errorMsg = Messages.CapellaSessionHelper_Repair_Migrate_Message;
        }
      } else if (cause instanceof UnresolvedReferenceException) {
        errorMsg = cause.getMessage();
      }
    } else if (exception instanceof ClassCastException) {
      if (exception.getMessage().indexOf("org.eclipse.sirius") >= 0) { //$NON-NLS-1$
        errorMsg = Messages.CapellaSessionHelper_Repair_Migrate_Message;
      }
    } else if (exception instanceof RuntimeException){
      Throwable cause = ((RuntimeException) exception).getCause();
        if(cause instanceof FeatureNotFoundException){
          errorMsg = Messages.CapellaSessionHelper_Repair_Migrate_Project_Message;
        }
    }
    
    // Deal with unknown errors.
    if (null == errorMsg) {
      errorMsg = Messages.CapellaSessionHelper_UnknownError_Message;
    }
    // Log exception...
    __logger.debug(new EmbeddedMessage(exception.getMessage(), IReportManagerDefaultComponents.MODEL));
    __logger.error(new EmbeddedMessage(errorMsg, IReportManagerDefaultComponents.MODEL));
    return errorMsg;
  }

  /**
   * Utility method to create and open a new local session.
   * @param semanticModels the IFile's of the model to use a semantic models.
   * @param airdURI the URI of the target diagrams file that will keep all the session data. The resource targeted by the URI will be created.
   * @return the newly created session.
   * @throws IOException if the diagrams file can't be created.
   * @throws PartInitException if the model content view can't be activated.
   * @throws InterruptedException on interruption while creating the session.
   * @throws InvocationTargetException on invocation error.
   */
  public static Session createLocalSession(List<IFile> semanticModels, URI airdURI, IProgressMonitor monitor) throws IOException, PartInitException,
      InvocationTargetException, InterruptedException {
    List<URI> semanticURIs = new ArrayList<URI>();
    for (IFile semanticModelFile : semanticModels) {
      semanticURIs.add(EcoreUtil2.getURI(semanticModelFile));
    }

    if (!semanticURIs.isEmpty()) {
      LocalSessionCreationOperation sessionCreationOperation = new LocalSessionCreationOperation(semanticURIs, airdURI);
      sessionCreationOperation.run(monitor);
      return sessionCreationOperation.getCreatedSession();
    }
    throw new IllegalArgumentException("No semantic model might be loaded."); //$NON-NLS-1$
  }
}

class LocalSessionCreationOperation extends WorkspaceModifyOperation {
  // Log4j reference logger.
  static final Logger __logger = ReportManagerRegistry.getInstance().subscribe(IReportManagerDefaultComponents.MODEL);
  List<URI> _semanticURIs;
  URI _airdURI;
  Session _session;

  /**
   * Constructs the workspace operation which allow to create a local session.
   * @param airdURI The aird resource.
   * @param semanticURIs The loaded resources list.
   */
  public LocalSessionCreationOperation(List<URI> semanticURIs, URI airdURI) {
    _semanticURIs = semanticURIs;
    _airdURI = airdURI;
  }

  /**
   * @see org.eclipse.ui.actions.WorkspaceModifyOperation#execute(org.eclipse.core.runtime.IProgressMonitor)
   */
  @Override
  protected void execute(IProgressMonitor monitor) throws CoreException, InterruptedException {
    SubMonitor progress = SubMonitor.convert(monitor, Messages.CapellaSessionHelper_CreateSession_Title, 1);
    // Begins the task.
    try {

      monitor.beginTask("Representations resource creation", 3);//$NON-NLS-1$

      monitor.subTask("Session creation");//$NON-NLS-1$
      _session = SessionFactory.INSTANCE.createSession(_airdURI, new SubProgressMonitor(monitor, 1));

      monitor.subTask("Add semantic model to the session");//$NON-NLS-1$
      TransactionHelper.getExecutionManager(_session).execute(new AbstractReadWriteCommand() {
        @Override
        public void run() {
          for (URI uri : _semanticURIs) {
            _session.addSemanticResource(uri, new NullProgressMonitor());
          }
        }
      });

    } catch (CoreException exception) {
      __logger.error(exception.getMessage(), exception);
    }
    progress.worked(1);
  }

  /**
   * Return the created session.
   * @return
   */
  public Session getCreatedSession() {
    return _session;
  }
}
