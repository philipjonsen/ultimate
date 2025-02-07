/*
 * Copyright (C) 2016 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2016 University of Freiburg
 *
 * This file is part of the ULTIMATE WitnessPrinter plug-in.
 *
 * The ULTIMATE WitnessPrinter plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE WitnessPrinter plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE WitnessPrinter plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE WitnessPrinter plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE WitnessPrinter plug-in grant you additional permission
 * to convey the resulting work.
 */

package de.uni_freiburg.informatik.ultimate.witnessprinter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.core.lib.results.AllSpecificationsHoldResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.CounterExampleResult;
import de.uni_freiburg.informatik.ultimate.core.lib.results.LassoShapedNonTerminationArgument;
import de.uni_freiburg.informatik.ultimate.core.lib.results.ResultUtil;
import de.uni_freiburg.informatik.ultimate.core.lib.translation.BacktranslatedCFG;
import de.uni_freiburg.informatik.ultimate.core.model.IOutput;
import de.uni_freiburg.informatik.ultimate.core.model.models.ILocation;
import de.uni_freiburg.informatik.ultimate.core.model.models.ModelType;
import de.uni_freiburg.informatik.ultimate.core.model.observers.IObserver;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceProvider;
import de.uni_freiburg.informatik.ultimate.core.model.results.IResult;
import de.uni_freiburg.informatik.ultimate.core.model.services.IBacktranslationService;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.core.model.translation.IBacktranslatedCFG;
import de.uni_freiburg.informatik.ultimate.core.model.translation.IProgramExecution;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IcfgGraphProvider;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.BoogieIcfgContainer;
import de.uni_freiburg.informatik.ultimate.witnessprinter.preferences.PreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.witnessprinter.yaml.YamlCorrectnessWitnessGenerator;

/**
 *
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 *
 */
public class WitnessPrinter implements IOutput {
	private static final String GRAPHML = ".graphml";
	private static final String YAML = ".yaml";

	private ILogger mLogger;
	private IUltimateServiceProvider mServices;
	private RCFGCatcher mRCFGCatcher;
	private boolean mMatchingModel;

	@Override
	public boolean isGuiRequired() {
		return false;
	}

	@Override
	public ModelQuery getModelQuery() {
		return ModelQuery.ALL;
	}

	@Override
	public List<String> getDesiredToolIds() {
		return Collections.emptyList();
	}

	@Override
	public void setInputDefinition(final ModelType graphType) {
		if ("de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder".equals(graphType.getCreator())) {
			mMatchingModel = true;
		} else {
			mMatchingModel = false;
		}
	}

	@Override
	public List<IObserver> getObservers() {
		if (mMatchingModel) {
			// we should create this class somewere in cacsl s.t. we get the correct parameters -- perhaps translation
			// service
			mRCFGCatcher = new RCFGCatcher();
			return Collections.singletonList(mRCFGCatcher);
		}
		return Collections.emptyList();
	}

	@Override
	public void setServices(final IUltimateServiceProvider services) {
		mServices = services;
		mLogger = services.getLoggingService().getLogger(Activator.PLUGIN_ID);
	}

	@Override
	public void init() {
		// not needed
	}

	@Override
	public void finish() {
		// determine if there are true or false witnesses
		final List<IResult> results = mServices.getResultService().getResults().entrySet().stream()
				.flatMap(a -> a.getValue().stream()).collect(Collectors.toList());
		final IPreferenceProvider ups = mServices.getPreferenceProvider(Activator.PLUGIN_ID);
		final boolean createGraphML = ups.getBoolean(PreferenceInitializer.LABEL_GENERATE_GRAPHML_WITNESS);
		final boolean createYaml = ups.getBoolean(PreferenceInitializer.LABEL_GENERATE_YAML_WITNESS);

		final List<ResultWitness> witnesses;
		if (results.stream().anyMatch(a -> a instanceof CounterExampleResult<?, ?, ?>)) {
			mLogger.info("Generating witness for reachability counterexample");
			witnesses = generateReachabilityCounterexampleWitness(results, createGraphML, createYaml);
		} else if (results.stream().anyMatch(a -> a instanceof LassoShapedNonTerminationArgument<?, ?>)) {
			mLogger.info("Generating witness for non-termination counterexample", createGraphML, createYaml);
			witnesses = generateNonTerminationWitness(results, createGraphML, createYaml);
		} else if (results.stream().anyMatch(a -> a instanceof AllSpecificationsHoldResult)) {
			mLogger.info("Generating witness for correct program");
			witnesses = generateProofWitness(results, createGraphML, createYaml);
		} else {
			mLogger.info("No result that supports witness generation found");
			witnesses = List.of();
		}
		try {
			new WitnessManager(mLogger, mServices).run(witnesses);
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private List<ResultWitness> generateProofWitness(final List<IResult> results, final boolean createGraphML,
			final boolean createYaml) {
		final AllSpecificationsHoldResult result =
				ResultUtil.filterResults(results, AllSpecificationsHoldResult.class).stream().findFirst().orElse(null);
		final IBacktranslationService backtrans = mServices.getBacktranslationService();
		final BoogieIcfgContainer root = mRCFGCatcher.getModel();
		final String filename = ILocation.getAnnotation(root).getFileName();
		final BacktranslatedCFG<?, IcfgEdge> origCfg =
				new BacktranslatedCFG<>(filename, IcfgGraphProvider.getVirtualRoot(root), IcfgEdge.class);
		final IBacktranslatedCFG<?, ?> translateCFG = backtrans.translateCFG(origCfg);
		final List<ResultWitness> witnesses = new ArrayList<>();
		if (createGraphML) {
			witnesses.add(new ResultWitness(filename, GRAPHML,
					new CorrectnessWitnessGenerator<>(translateCFG, mLogger, mServices).makeGraphMLString(), result));
		}
		if (createYaml) {
			witnesses.add(new ResultWitness(filename, YAML,
					new YamlCorrectnessWitnessGenerator(translateCFG, mLogger, mServices).makeYamlString(), result));
		}
		return witnesses;
	}

	@SuppressWarnings("rawtypes")
	private List<ResultWitness> generateReachabilityCounterexampleWitness(final List<IResult> results,
			final boolean createGraphML, final boolean createYaml) {
		final List<ResultWitness> suppliers = new ArrayList<>();
		final Collection<CounterExampleResult> cexResults =
				ResultUtil.filterResults(results, CounterExampleResult.class);
		final IBacktranslationService backtrans = mServices.getBacktranslationService();
		final BoogieIcfgContainer root = mRCFGCatcher.getModel();
		final String filename = ILocation.getAnnotation(root).getFileName();

		for (final CounterExampleResult<?, ?, ?> cex : cexResults) {
			final IProgramExecution<?, ?> backtransPe = backtrans.translateProgramExecution(cex.getProgramExecution());
			if (createGraphML) {
				final String witness =
						new ViolationWitnessGenerator<>(backtransPe, mLogger, mServices).makeGraphMLString();
				suppliers.add(new ResultWitness(filename, GRAPHML, witness, cex));
			}
			// TODO: Add support for YAML
		}
		return suppliers;
	}

	@SuppressWarnings("rawtypes")
	private List<ResultWitness> generateNonTerminationWitness(final List<IResult> results, final boolean createGraphML,
			final boolean createYaml) {
		final List<ResultWitness> suppliers = new ArrayList<>();
		final Collection<LassoShapedNonTerminationArgument> cexResults =
				ResultUtil.filterResults(results, LassoShapedNonTerminationArgument.class);
		final IBacktranslationService backtrans = mServices.getBacktranslationService();
		final BoogieIcfgContainer root = mRCFGCatcher.getModel();
		final String filename = ILocation.getAnnotation(root).getFileName();

		for (final LassoShapedNonTerminationArgument<?, ?> cex : cexResults) {
			if (createGraphML) {
				final String witness = getWitness(backtrans, cex);
				suppliers.add(new ResultWitness(filename, GRAPHML, witness, cex));
			}
			// TODO: Add support for YAML
		}
		return suppliers;
	}

	@SuppressWarnings("unchecked")
	private <TE, T> String getWitness(final IBacktranslationService backtrans,
			final LassoShapedNonTerminationArgument<?, ?> cex) {
		final IProgramExecution<TE, T> stem =
				(IProgramExecution<TE, T>) backtrans.translateProgramExecution(cex.getStemExecution());
		final IProgramExecution<TE, T> loop =
				(IProgramExecution<TE, T>) backtrans.translateProgramExecution(cex.getLoopExecution());
		return new ViolationWitnessGenerator<>(stem, loop, mLogger, mServices).makeGraphMLString();
	}

	@Override
	public String getPluginName() {
		return Activator.PLUGIN_NAME;
	}

	@Override
	public String getPluginID() {
		return Activator.PLUGIN_ID;
	}

	@Override
	public IPreferenceInitializer getPreferences() {
		return new PreferenceInitializer();
	}

	public static class ResultWitness {
		private final String mSourceFile;
		private final String mWitnessEnding;
		private final String mWitnessString;
		private final IResult mResult;

		public ResultWitness(final String sourceFile, final String witnessEnding, final String witnessString,
				final IResult result) {
			mSourceFile = sourceFile;
			mWitnessEnding = witnessEnding;
			mWitnessString = witnessString;
			mResult = result;
		}

		public String getSourceFile() {
			return mSourceFile;
		}

		public String getWitnessEnding() {
			return mWitnessEnding;
		}

		public String getWitnessString() {
			return mWitnessString;
		}

		public IResult getResult() {
			return mResult;
		}
	}
}
