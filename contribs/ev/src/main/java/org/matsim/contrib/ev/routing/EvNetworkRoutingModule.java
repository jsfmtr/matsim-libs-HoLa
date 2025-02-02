/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package org.matsim.contrib.ev.routing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.ev.EvConfigGroup;
import org.matsim.contrib.ev.charging.VehicleChargingHandler;
import org.matsim.contrib.ev.discharging.AuxEnergyConsumption;
import org.matsim.contrib.ev.discharging.DriveEnergyConsumption;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.fleet.ElectricVehicleImpl;
import org.matsim.contrib.ev.fleet.ElectricVehicleSpecification;
import org.matsim.contrib.ev.infrastructure.ChargerSpecification;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecification;
import org.matsim.contrib.common.util.StraightLineKnnFinder;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.router.LinkWrapperFacility;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.RoutingRequest;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;
import org.matsim.facilities.Facility;
import org.matsim.vehicles.Vehicle;

/**
 * This network Routing module adds stages for re-charging into the Route.
 * This wraps a "computer science" {@link LeastCostPathCalculator}, which routes from a node to another node, into something that
 * routes from a {@link Facility} to another {@link Facility}, as we need in MATSim.
 *
 * @author jfbischoff
 */

public final class EvNetworkRoutingModule implements RoutingModule {

	private final String mode;

	private final Network network;
	private final RoutingModule delegate;
	private final ElectricFleetSpecification electricFleet;
	private final ChargingInfrastructureSpecification chargingInfrastructureSpecification;
	private final Random random = MatsimRandom.getLocalInstance();
	private final TravelTime travelTime;
	private final DriveEnergyConsumption.Factory driveConsumptionFactory;
	private final AuxEnergyConsumption.Factory auxConsumptionFactory;
	private final String stageActivityModePrefix;
	private final String vehicleSuffix;
	private final EvConfigGroup evConfigGroup;

	public EvNetworkRoutingModule(final String mode, final Network network, RoutingModule delegate,
			ElectricFleetSpecification electricFleet,
			ChargingInfrastructureSpecification chargingInfrastructureSpecification, TravelTime travelTime,
			DriveEnergyConsumption.Factory driveConsumptionFactory, AuxEnergyConsumption.Factory auxConsumptionFactory,
			EvConfigGroup evConfigGroup) {
		this.travelTime = travelTime;
		Gbl.assertNotNull(network);
		this.delegate = delegate;
		this.network = network;
		this.mode = mode;
		this.electricFleet = electricFleet;
		this.chargingInfrastructureSpecification = chargingInfrastructureSpecification;
		this.driveConsumptionFactory = driveConsumptionFactory;
		this.auxConsumptionFactory = auxConsumptionFactory;
		stageActivityModePrefix = mode + VehicleChargingHandler.CHARGING_IDENTIFIER;
		this.evConfigGroup = evConfigGroup;
		this.vehicleSuffix = mode.equals(TransportMode.car) ? "" : "_" + mode;
	}

	@Override
	public List<? extends PlanElement> calcRoute(RoutingRequest request) {
		final Facility fromFacility = request.getFromFacility();
		final Facility toFacility = request.getToFacility();
		final double departureTime = request.getDepartureTime();
		final Person person = request.getPerson();

		List<? extends PlanElement> basicRoute = delegate.calcRoute(request);
		Id<Vehicle> evId = Id.create(person.getId() + vehicleSuffix, Vehicle.class);
		if (!electricFleet.getVehicleSpecifications().containsKey(evId)) {
			return basicRoute;
		} else {
			Leg basicLeg = (Leg)basicRoute.get(0);
			ElectricVehicleSpecification ev = electricFleet.getVehicleSpecifications().get(evId);
			double overallDistance = basicLeg.getRoute().getDistance();
			double rangePerTripWithoutStop = 300 * Math.pow(10, 3);
			double numberOfStops = Math.floor(overallDistance / rangePerTripWithoutStop); // Stop after 300km

			if (numberOfStops < 1) {
				return basicRoute;
			} else {
				List<Link> stopLocations = new ArrayList<>();
				NetworkRoute route = (NetworkRoute)basicLeg.getRoute();
				List<Link> links = NetworkUtils.getLinks(network, route.getLinkIds());

				double currentDistance = 0;

				for (Link l : links) {
					currentDistance += l.getLength();
					if (currentDistance > rangePerTripWithoutStop) {
						stopLocations.add(l);
						currentDistance = 0;
					}
				}
				List<PlanElement> stagedRoute = new ArrayList<>();
				Facility lastFrom = fromFacility;
				double lastArrivaltime = departureTime;
				int stopCounter = 0;

				for (Link stopLocation : stopLocations) {
					stopCounter += 1;

					StraightLineKnnFinder<Link, ChargerSpecification> straightLineKnnFinder = new StraightLineKnnFinder<>(
							2, Link::getCoord, s -> network.getLinks().get(s.getLinkId()).getCoord());
					List<ChargerSpecification> nearestChargers = straightLineKnnFinder.findNearest(stopLocation,
							chargingInfrastructureSpecification.getChargerSpecifications()
									.values()
									.stream()
									.filter(charger -> ev.getChargerTypes().contains(charger.getChargerType())));
					ChargerSpecification selectedCharger = nearestChargers.get(random.nextInt(1));
					Link selectedChargerLink = network.getLinks().get(selectedCharger.getLinkId());
					Facility nexttoFacility = new LinkWrapperFacility(selectedChargerLink);
					if (nexttoFacility.getLinkId().equals(lastFrom.getLinkId())) {
						continue;
					}
					List<? extends PlanElement> routeSegment = delegate.calcRoute(DefaultRoutingRequest.of(lastFrom, nexttoFacility,
							lastArrivaltime, person, request.getAttributes()));
					Leg lastLeg = (Leg)routeSegment.get(0);
					lastArrivaltime = lastLeg.getDepartureTime().seconds() + lastLeg.getTravelTime().seconds();
					stagedRoute.add(lastLeg);
					Activity chargeAct = PopulationUtils.createStageActivityFromCoordLinkIdAndModePrefix(selectedChargerLink.getCoord(),
							selectedChargerLink.getId(), stageActivityModePrefix);
					if (stopCounter % 2 != 0){
						// If the StopCounter is odd, the max charging time is 45min. -> Lenkzeitunterbrechung
						chargeAct.setMaximumDuration(evConfigGroup.maximumChargeTime); // max charging time 45min
					}else{
						// else the min rest time is set as the max charging duration
						chargeAct.setMaximumDuration(11*60*60);
					}
					lastArrivaltime += chargeAct.getMaximumDuration().seconds();
					stagedRoute.add(chargeAct);
					lastFrom = nexttoFacility;
				}
				stagedRoute.addAll(delegate.calcRoute(DefaultRoutingRequest.of(lastFrom, toFacility, lastArrivaltime, person, request.getAttributes())));

				return stagedRoute;

			}

		}
	}

	private Map<Link, Double> estimateConsumption(ElectricVehicleSpecification ev, Leg basicLeg) {
		Map<Link, Double> consumptions = new LinkedHashMap<>();
		NetworkRoute route = (NetworkRoute)basicLeg.getRoute();
		List<Link> links = NetworkUtils.getLinks(network, route.getLinkIds());
		ElectricVehicle pseudoVehicle = ElectricVehicleImpl.create(ev, driveConsumptionFactory, auxConsumptionFactory,
				v -> charger -> {
					throw new UnsupportedOperationException();
				});
		DriveEnergyConsumption driveEnergyConsumption = pseudoVehicle.getDriveEnergyConsumption();
		AuxEnergyConsumption auxEnergyConsumption = pseudoVehicle.getAuxEnergyConsumption();
		double lastCharge = pseudoVehicle.getBattery().getCharge();
		double linkEnterTime = basicLeg.getDepartureTime().seconds();
		for (Link l : links) {
			double travelT = travelTime.getLinkTravelTime(l, basicLeg.getDepartureTime().seconds(), null, null);

			double consumption = driveEnergyConsumption.calcEnergyConsumption(l, travelT, linkEnterTime)
					+ auxEnergyConsumption.calcEnergyConsumption(basicLeg.getDepartureTime().seconds(), travelT, l.getId());
			// to accomodate for ERS, where energy charge is directly implemented in the consumption model
			consumptions.put(l, consumption);
			linkEnterTime += travelT;
		}
		return consumptions;
	}

	@Override
	public String toString() {
		return "[NetworkRoutingModule: mode=" + this.mode + "]";
	}

}
